package com.checkout.payment.gateway.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentStatus {
  AUTHORIZED("Authorized"),
  DECLINED("Declined");

  private final String wireValue;

  PaymentStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  @JsonValue
  public String getName() {
    return wireValue;
  }
}
