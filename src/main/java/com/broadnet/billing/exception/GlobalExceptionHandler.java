package com.broadnet.billing.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for all billing API controllers.
 *
 * Maps every exception type to the correct HTTP status and ErrorResponse body.
 * Architecture Plan error handling rules:
 *  - Webhook signature failures       → 400
 *  - Validation / bad request         → 400
 *  - Unauthorized                     → 401
 *  - Usage limit exceeded / Forbidden → 403
 *  - Not found                        → 404
 *  - Conflict / duplicate / locking   → 409
 *  - Invalid state / plan change      → 422
 *  - Stripe API failure               → 502
 *  - All other unexpected errors      → 500
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // -------------------------------------------------------------------------
    // 400 Bad Request
    // -------------------------------------------------------------------------

    /**
     * Spring @Valid / @Validated bean validation failures.
     * Collects all field-level errors into ErrorResponse.fieldErrors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(fe.getRejectedValue())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse body = ErrorResponse.builder()
                .status(400)
                .errorCode("VALIDATION_FAILED")
                .message("Request validation failed")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        log.warn("Validation failed on {}: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Missing required request parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        return badRequest("MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing",
                request);
    }

    /**
     * Wrong type for a request parameter (e.g. string where enum expected).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String message = String.format("Invalid value '%s' for parameter '%s'",
                ex.getValue(), ex.getName());
        return badRequest("INVALID_PARAMETER_TYPE", message, request);
    }

    /**
     * Webhook signature verification failure or payload parse error.
     * Architecture Plan: "Invalid Signature → 400 Bad Request"
     * Only INVALID_SIGNATURE maps to 400; other webhook failures map to 500.
     */
    @ExceptionHandler(WebhookProcessingException.class)
    public ResponseEntity<ErrorResponse> handleWebhookProcessingException(
            WebhookProcessingException ex,
            HttpServletRequest request) {

        if (ex.isSignatureFailure()) {
            log.warn("Webhook signature failure on {}: {}", request.getRequestURI(), ex.getMessage());
            return badRequest(ex.getErrorCode(), ex.getMessage(), request);
        }

        log.error("Webhook processing error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return internalError(ex.getErrorCode(), ex.getMessage(), request);
    }

    /**
     * IllegalArgumentException — used internally for programming errors like
     * "No plan found in subscription" (from architecture plan code samples).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("IllegalArgumentException on {}: {}", request.getRequestURI(), ex.getMessage());
        return badRequest("INVALID_ARGUMENT", ex.getMessage(), request);
    }

    // -------------------------------------------------------------------------
    // 403 Forbidden
    // -------------------------------------------------------------------------

    /**
     * Usage limit exceeded (answers, KB pages, agents, users).
     * Architecture Plan API response: { success: false, error: "ANSWER_LIMIT_EXCEEDED", ... }
     */
    @ExceptionHandler(UsageLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleUsageLimitExceeded(
            UsageLimitExceededException ex,
            HttpServletRequest request) {

        ErrorResponse.UsageDetails details = null;
        if (ex.getUsed() != null && ex.getLimit() != null) {
            details = ErrorResponse.UsageDetails.builder()
                    .used(ex.getUsed())
                    .limit(ex.getLimit())
                    .remaining(ex.getLimit() - ex.getUsed())
                    .build();
        }

        ErrorResponse body = ErrorResponse.builder()
                .status(403)
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .usageDetails(details)
                .build();

        log.info("Usage limit exceeded on {}: errorCode={}, used={}, limit={}",
                request.getRequestURI(), ex.getErrorCode(), ex.getUsed(), ex.getLimit());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // -------------------------------------------------------------------------
    // 404 Not Found
    // -------------------------------------------------------------------------

    /**
     * Resource not found — company_billing, plan, addon, invoice, etc.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.info("Resource not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.of(404, ex.getErrorCode(), ex.getMessage(), request.getRequestURI())
        );
    }

    // -------------------------------------------------------------------------
    // 409 Conflict
    // -------------------------------------------------------------------------

    /**
     * Optimistic locking conflict — version mismatch after all retries exhausted.
     * Architecture Plan: "Retry with exponential backoff"; only surfaces as 409
     * if all retries fail.
     */
    @ExceptionHandler(OptimisticLockingException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(
            OptimisticLockingException ex,
            HttpServletRequest request) {

        log.warn("Optimistic locking conflict on {} after all retries: {}",
                request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of(409, ex.getErrorCode(), ex.getMessage(), request.getRequestURI())
        );
    }

    /**
     * Spring Data JPA optimistic locking failure — maps same as our custom one.
     * Catches ObjectOptimisticLockingFailureException from JPA @Version.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleSpringOptimisticLocking(
            OptimisticLockingFailureException ex,
            HttpServletRequest request) {

        log.warn("JPA optimistic locking failure on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of(409, "OPTIMISTIC_LOCK_CONFLICT",
                        "Concurrent update conflict. Please retry.",
                        request.getRequestURI())
        );
    }

    /**
     * Duplicate resource — resource already exists.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex,
            HttpServletRequest request) {

        log.info("Duplicate resource on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of(409, ex.getErrorCode(), ex.getMessage(), request.getRequestURI())
        );
    }

    // -------------------------------------------------------------------------
    // 422 Unprocessable Entity
    // -------------------------------------------------------------------------

    /**
     * Invalid plan change request (same plan, no subscription, enterprise account, etc.).
     */
    @ExceptionHandler(InvalidPlanChangeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPlanChange(
            InvalidPlanChangeException ex,
            HttpServletRequest request) {

        log.info("Invalid plan change on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponse.of(422, ex.getErrorCode(), ex.getMessage(), request.getRequestURI())
        );
    }

    /**
     * Invalid billing state transition
     * (e.g. invoicing before calculation, reactivating non-canceling subscription).
     */
    @ExceptionHandler(BillingStateException.class)
    public ResponseEntity<ErrorResponse> handleBillingState(
            BillingStateException ex,
            HttpServletRequest request) {

        log.info("Billing state error on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponse.of(422, ex.getErrorCode(), ex.getMessage(), request.getRequestURI())
        );
    }

    /**
     * Enterprise setup precondition failure.
     */
    @ExceptionHandler(EnterpriseSetupException.class)
    public ResponseEntity<ErrorResponse> handleEnterpriseSetup(
            EnterpriseSetupException ex,
            HttpServletRequest request) {

        log.info("Enterprise setup error on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponse.of(422, ex.getErrorCode(), ex.getMessage(), request.getRequestURI())
        );
    }

    // -------------------------------------------------------------------------
    // 502 Bad Gateway
    // -------------------------------------------------------------------------

    /**
     * Stripe API call failure — Stripe is unreachable or returned unexpected response.
     * Architecture Plan: "Stripe API failures: Log and alert, don't block user operations"
     * 502 signals the upstream service (Stripe) failed, not our code.
     */
    @ExceptionHandler(StripeIntegrationException.class)
    public ResponseEntity<ErrorResponse> handleStripeIntegration(
            StripeIntegrationException ex,
            HttpServletRequest request) {

        log.error("Stripe API error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                ErrorResponse.of(502, ex.getErrorCode(),
                        "Payment service temporarily unavailable. Please try again.",
                        request.getRequestURI())
        );
    }

    // -------------------------------------------------------------------------
    // 500 Internal Server Error — catch-all
    // -------------------------------------------------------------------------

    /**
     * Any base BillingException not handled above — shouldn't normally occur
     * if all subtypes are handled, but provides a safe fallback.
     */
    @ExceptionHandler(BillingException.class)
    public ResponseEntity<ErrorResponse> handleBillingException(
            BillingException ex,
            HttpServletRequest request) {

        log.error("Unhandled BillingException on {}: errorCode={}, message={}",
                request.getRequestURI(), ex.getErrorCode(), ex.getMessage(), ex);
        return internalError(ex.getErrorCode(), ex.getMessage(), request);
    }

    /**
     * Catch-all for any unexpected exception.
     * Returns a generic 500 — never leaks internal details to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return internalError("INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later.",
                request);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> badRequest(String errorCode, String message,
                                                      HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(400, errorCode, message, request.getRequestURI())
        );
    }

    private ResponseEntity<ErrorResponse> internalError(String errorCode, String message,
                                                         HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of(500, errorCode, message, request.getRequestURI())
        );
    }
}
