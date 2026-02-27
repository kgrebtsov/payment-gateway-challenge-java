package com.checkout.payment.gateway.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ErrorStatus {
  REJECTED("Rejected");

  private final String wireValue;

  ErrorStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  @JsonValue
  public String getValue() {
    return wireValue;
  }
}
