package com.ggukmoney.beanzip.global.common;

import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import com.ggukmoney.beanzip.global.logging.RequestLogContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DEFAULT_VALIDATION_MESSAGE = "요청 값이 올바르지 않습니다.";

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatusCode statusCode = exception.getStatusCode();
        String code = mapStatusCode(statusCode, exception.getReason());
        String message = resolveMessage(exception.getReason(), defaultMessage(statusCode));
        request.setAttribute(AuthRequestAttributes.ERROR_CODE, code);
        logByStatus(statusCode, code, message, request, exception);

        return ResponseEntity.status(statusCode).body(ApiErrorResponse.failure(code, message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        return validationFailure(resolveFieldErrorMessage(exception), request, exception);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(BindException exception, HttpServletRequest request) {
        return validationFailure(resolveFieldErrorMessage(exception), request, exception);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> resolveMessage(violation.getMessage(), DEFAULT_VALIDATION_MESSAGE))
                .orElse(DEFAULT_VALIDATION_MESSAGE);
        return validationFailure(message, request, exception);
    }

    @ExceptionHandler(ValidationFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailureException(
            ValidationFailureException exception,
            HttpServletRequest request
    ) {
        return validationFailure(resolveMessage(exception.getMessage(), DEFAULT_VALIDATION_MESSAGE), request, exception);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidRequestException(Exception exception, HttpServletRequest request) {
        request.setAttribute(AuthRequestAttributes.ERROR_CODE, "INVALID_REQUEST");
        log.warn("Invalid request: code={} method={} path={} traceId={} errorType={}",
                "INVALID_REQUEST",
                request.getMethod(),
                request.getRequestURI(),
                RequestLogContext.currentTraceIdOrDefault(),
                exception.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(ApiErrorResponse.failure("INVALID_REQUEST", "요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        request.setAttribute(AuthRequestAttributes.ERROR_CODE, "INVALID_REQUEST");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiErrorResponse.failure("INVALID_REQUEST", "요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception, HttpServletRequest request) {
        request.setAttribute(AuthRequestAttributes.ERROR_CODE, "INTERNAL_ERROR");
        log.error("Unexpected server error: code={} method={} path={} traceId={} exceptionType={}",
                "INTERNAL_ERROR",
                request.getMethod(),
                request.getRequestURI(),
                RequestLogContext.currentTraceIdOrDefault(),
                exception.getClass().getSimpleName(),
                exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.failure("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."));
    }

    private ResponseEntity<ApiErrorResponse> validationFailure(String message, HttpServletRequest request, Exception exception) {
        request.setAttribute(AuthRequestAttributes.ERROR_CODE, "VALIDATION_FAILED");
        log.warn("Validation failed: code={} method={} path={} traceId={} errorType={}",
                "VALIDATION_FAILED",
                request.getMethod(),
                request.getRequestURI(),
                RequestLogContext.currentTraceIdOrDefault(),
                exception.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(ApiErrorResponse.failure("VALIDATION_FAILED", message));
    }

    private String resolveFieldErrorMessage(BindException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        if (fieldError == null) {
            return DEFAULT_VALIDATION_MESSAGE;
        }
        return resolveMessage(fieldError.getDefaultMessage(), DEFAULT_VALIDATION_MESSAGE);
    }

    private String mapStatusCode(HttpStatusCode statusCode, String reason) {
        if (StringUtils.hasText(reason) && reason.matches("[A-Z0-9_]+")) {
            return reason;
        }
        int status = statusCode.value();
        if (status == HttpStatus.BAD_REQUEST.value()) {
            return "INVALID_REQUEST";
        }
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return "AUTH_REQUIRED";
        }
        if (status == HttpStatus.FORBIDDEN.value()) {
            return "FORBIDDEN";
        }
        if (status == HttpStatus.CONFLICT.value()) {
            return "CONFLICT";
        }
        if (status == HttpStatus.NOT_FOUND.value()) {
            return "NOT_FOUND";
        }
        return statusCode.is5xxServerError() ? "INTERNAL_ERROR" : "INVALID_REQUEST";
    }

    private String defaultMessage(HttpStatusCode statusCode) {
        if (statusCode.value() == HttpStatus.UNAUTHORIZED.value()) {
            return "인증이 필요합니다.";
        }
        if (statusCode.value() == HttpStatus.FORBIDDEN.value()) {
            return "권한이 없습니다.";
        }
        if (statusCode.is5xxServerError()) {
            return "서버 내부 오류가 발생했습니다.";
        }
        return DEFAULT_VALIDATION_MESSAGE;
    }

    private String resolveMessage(String message, String defaultMessage) {
        return StringUtils.hasText(message) && !message.matches("[A-Z0-9_]+") ? message : defaultMessage;
    }

    private void logByStatus(
            HttpStatusCode statusCode,
            String code,
            String message,
            HttpServletRequest request,
            ResponseStatusException exception
    ) {
        if (statusCode.is5xxServerError()) {
            log.error("Request failed: code={} method={} path={} traceId={} message={} exceptionType={}",
                    code, request.getMethod(), request.getRequestURI(), RequestLogContext.currentTraceIdOrDefault(),
                    message, exception.getClass().getSimpleName());
            return;
        }
        log.warn("Request failed: code={} method={} path={} traceId={} message={} exceptionType={}",
                code, request.getMethod(), request.getRequestURI(), RequestLogContext.currentTraceIdOrDefault(),
                message, exception.getClass().getSimpleName());
    }
}
