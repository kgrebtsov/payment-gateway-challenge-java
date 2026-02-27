package com.checkout.payment.gateway.exception;

import java.io.Serial;

public class BankClientException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public BankClientException(String message) {
    super(message);
  }

  public BankClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
