package com.checkout.payment.gateway.exception;

import java.io.Serial;

public class IdempotencyConflictException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  public IdempotencyConflictException(String message) {
    super(message);
  }
}
