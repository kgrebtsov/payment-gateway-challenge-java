package com.checkout.payment.gateway.service.creation;

import com.checkout.payment.gateway.enums.PaymentStatus;

public record PaymentDecision(PaymentStatus status) {
}
