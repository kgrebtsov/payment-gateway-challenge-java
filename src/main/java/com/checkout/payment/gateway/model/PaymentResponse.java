package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;

public record PaymentResponse(
    UUID paymentId,
    PaymentStatus status,
    Integer amount,
    String currency,
    String maskedCardNumber,
    String cardLast4,
    Integer expiryMonth,
    Integer expiryYear
) {
  public static PaymentResponse from(PaymentRecord payment) {
    return new PaymentResponse(
        payment.paymentId(),
        payment.status(),
        payment.amount(),
        payment.currency(),
        "**** **** **** " + payment.cardLast4(),
        payment.cardLast4(),
        payment.expiryMonth(),
        payment.expiryYear());
  }
}
