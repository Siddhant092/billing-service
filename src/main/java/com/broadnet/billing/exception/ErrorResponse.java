package com.broadnet.billing.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard error response body returned by the GlobalExceptionHandler.
 * Every error response from the billing API uses this shape.
 *
 * Example JSON:
 * {
 *   "status": 404,
 *   "errorCode": "RESOURCE_NOT_FOUND",
 *   "message": "BillingPlan not found with planCode: 'professional'",
 *   "timestamp": "2025-01-15T10:00:00",
 *   "path": "/api/billing/subscription/plans/professional"
 * }
 *
 * For validation errors (400), fieldErrors is also populated:
 * {
 *   "status": 400,
 *   "errorCode": "VALIDATION_FAILED",
 *   "message": "Request validation failed",
 *   "fieldErrors": [
 *     { "field": "planCode", "message": "must not be blank" }
 *   ]
 * }
 *
 * For usage limit errors (403), usageDetails is populated:
 * {
 *   "status": 403,
 *   "errorCode": "ANSWER_LIMIT_EXCEEDED",
 *   "message": "You've reached your answer limit. Upgrade plan or add a boost.",
 *   "usageDetails": { "used": 8000, "limit": 8000 }
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status;
    private String errorCode;
    private String message;
    private LocalDateTime timestamp;
    private String path;

    /** Populated for 400 validation errors — one entry per invalid field. */
    private List<FieldError> fieldErrors;

    /** Populated for 403 usage limit errors — shows used/limit context. */
    private UsageDetails usageDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageDetails {
        private Integer used;
        private Integer limit;
        private Integer remaining;
    }

    /** Convenience factory — timestamp defaults to now. */
    public static ErrorResponse of(int status, String errorCode, String message, String path) {
        return ErrorResponse.builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }
}
