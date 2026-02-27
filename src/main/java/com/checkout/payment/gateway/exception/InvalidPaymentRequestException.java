package com.checkout.payment.gateway.exception;

import java.io.Serial;

public class InvalidPaymentRequestException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final String field;

  public InvalidPaymentRequestException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
