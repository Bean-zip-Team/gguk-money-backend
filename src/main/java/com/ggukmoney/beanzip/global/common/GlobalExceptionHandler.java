package com.ggukmoney.beanzip.global.common;

import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import com.ggukmoney.beanzip.global.logging.RequestLogContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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
        ErrorCode errorCode = resolveErrorCode(exception);
        request.setAttribute(AuthRequestAttributes.ERROR_CODE, errorCode.code());
        logByStatus(errorCode, request, exception);

        return ResponseEntity.status(errorCode.status())
                .body(ApiErrorResponse.failure(errorCode.code(), errorCode.message()));
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
        ErrorCode errorCode = ErrorCode.COMMON_INVALID_REQUEST;
        request.setAttribute(AuthRequestAttributes.ERROR_CODE, errorCode.code());
        log.warn("Invalid request: code={} method={} path={} requestId={} errorType={}",
                errorCode.code(),
                request.getMethod(),
                request.getRequestURI(),
                RequestLogContext.currentRequestIdOrDefault(),
                exception.getClass().getSimpleName());
        return ResponseEntity.status(errorCode.status())
                .body(ApiErrorResponse.failure(errorCode.code(), errorCode.message()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return simpleFailure(ErrorCode.COMMON_UNSUPPORTED_MEDIA_TYPE, request, exception);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return simpleFailure(ErrorCode.COMMON_METHOD_NOT_ALLOWED, request, exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.COMMON_INTERNAL_SERVER_ERROR;
        request.setAttribute(AuthRequestAttributes.ERROR_CODE, errorCode.code());
        log.error("Unexpected server error: code={} method={} path={} requestId={} exceptionType={}",
                errorCode.code(),
                request.getMethod(),
                request.getRequestURI(),
                RequestLogContext.currentRequestIdOrDefault(),
                exception.getClass().getSimpleName(),
                exception);
        return ResponseEntity.status(errorCode.status())
                .body(ApiErrorResponse.failure(errorCode.code(), errorCode.message()));
    }

    private ResponseEntity<ApiErrorResponse> validationFailure(String message, HttpServletRequest request, Exception exception) {
        ErrorCode errorCode = ErrorCode.COMMON_VALIDATION_ERROR;
        request.setAttribute(AuthRequestAttributes.ERROR_CODE, errorCode.code());
        log.warn("Validation failed: code={} method={} path={} requestId={} errorType={}",
                errorCode.code(),
                request.getMethod(),
                request.getRequestURI(),
                RequestLogContext.currentRequestIdOrDefault(),
                exception.getClass().getSimpleName());
        return ResponseEntity.status(errorCode.status())
                .body(ApiErrorResponse.failure(errorCode.code(), resolveMessage(message, errorCode.message())));
    }

    private ResponseEntity<ApiErrorResponse> simpleFailure(ErrorCode errorCode, HttpServletRequest request, Exception exception) {
        request.setAttribute(AuthRequestAttributes.ERROR_CODE, errorCode.code());
        log.warn("Request failed: code={} method={} path={} requestId={} exceptionType={}",
                errorCode.code(),
                request.getMethod(),
                request.getRequestURI(),
                RequestLogContext.currentRequestIdOrDefault(),
                exception.getClass().getSimpleName());
        return ResponseEntity.status(errorCode.status())
                .body(ApiErrorResponse.failure(errorCode.code(), errorCode.message()));
    }

    private String resolveFieldErrorMessage(BindException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        if (fieldError == null) {
            return DEFAULT_VALIDATION_MESSAGE;
        }
        return resolveMessage(fieldError.getDefaultMessage(), DEFAULT_VALIDATION_MESSAGE);
    }

    private ErrorCode resolveErrorCode(ResponseStatusException exception) {
        ErrorCode errorCode = ErrorCode.fromCode(exception.getReason());
        if (errorCode != null) {
            return errorCode;
        }
        return ErrorCode.fromStatus(exception.getStatusCode().value());
    }

    private String resolveMessage(String message, String defaultMessage) {
        return StringUtils.hasText(message) ? message : defaultMessage;
    }

    private void logByStatus(
            ErrorCode errorCode,
            HttpServletRequest request,
            ResponseStatusException exception
    ) {
        if (errorCode.status().is5xxServerError()) {
            log.error("Request failed: code={} method={} path={} requestId={} message={} exceptionType={}",
                    errorCode.code(), request.getMethod(), request.getRequestURI(), RequestLogContext.currentRequestIdOrDefault(),
                    errorCode.message(), exception.getClass().getSimpleName());
            return;
        }
        log.warn("Request failed: code={} method={} path={} requestId={} message={} exceptionType={}",
                errorCode.code(), request.getMethod(), request.getRequestURI(), RequestLogContext.currentRequestIdOrDefault(),
                errorCode.message(), exception.getClass().getSimpleName());
    }
}
