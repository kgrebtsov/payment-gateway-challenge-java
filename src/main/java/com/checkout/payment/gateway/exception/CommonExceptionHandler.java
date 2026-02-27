package com.checkout.payment.gateway.exception;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

import com.checkout.payment.gateway.model.ErrorDetail;
import com.checkout.payment.gateway.model.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<ErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(this::toErrorDetail)
        .toList();
    return ResponseEntity.badRequest().body(ErrorResponse.rejected(errors));
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
    return ResponseEntity.badRequest()
        .body(ErrorResponse.rejected(ex.getHeaderName(), "Required header is missing"));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return ResponseEntity.badRequest()
        .body(ErrorResponse.rejected(ex.getName(), "Invalid value"));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest()
        .body(ErrorResponse.rejected("request", "Malformed JSON request"));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
    return ResponseEntity.badRequest()
        .body(ErrorResponse.rejected("request", ex.getMessage()));
  }

  @ExceptionHandler(InvalidPaymentRequestException.class)
  public ResponseEntity<ErrorResponse> handleInvalidPayment(InvalidPaymentRequestException ex) {
    return ResponseEntity.badRequest().body(ErrorResponse.rejected(ex.getField(), ex.getMessage()));
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(PaymentNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.single("paymentId", ex.getMessage()));
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.single("Idempotency-Key", ex.getMessage()));
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBankUnavailable(BankUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ErrorResponse.single("bank", ex.getMessage()));
  }

  @ExceptionHandler(BankClientException.class)
  public ResponseEntity<ErrorResponse> handleBankFailure(BankClientException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(ErrorResponse.single("bank", ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    LOG.error("unexpected.error",
        keyValue("event", "unexpected.error"),
        keyValue("exception", ex.getClass().getSimpleName()),
        keyValue("errorMessage", ex.getMessage()),
        ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.single("server", "Unexpected error"));
  }

  private ErrorDetail toErrorDetail(FieldError fieldError) {
    String message =
        fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage();
    return new ErrorDetail(fieldError.getField(), message);
  }
}
