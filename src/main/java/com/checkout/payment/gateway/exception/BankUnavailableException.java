package com.checkout.payment.gateway.exception;

import java.io.Serial;

public class BankUnavailableException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  public BankUnavailableException(String message) {
    super(message);
  }

  public BankUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
