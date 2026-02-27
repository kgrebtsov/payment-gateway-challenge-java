package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;

public record PaymentRecord(
    UUID paymentId,
    PaymentStatus status,
    Integer amount,
    String currency,
    String cardLast4,
    Integer expiryMonth,
    Integer expiryYear
) {

  public PaymentResponse toResponse() {
    return PaymentResponse.from(this);
  }
}
