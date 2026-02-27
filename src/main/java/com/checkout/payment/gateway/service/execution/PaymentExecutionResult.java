package com.checkout.payment.gateway.service.execution;

import com.checkout.payment.gateway.model.PaymentRecord;

public record PaymentExecutionResult(PaymentRecord payment, boolean replayed) {
}
