package com.checkout.payment.gateway.exception;

import java.io.Serial;

public class PaymentNotFoundException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  public PaymentNotFoundException(String message) {
    super(message);
  }
}
