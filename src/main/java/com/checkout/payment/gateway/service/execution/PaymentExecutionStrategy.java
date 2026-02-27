package com.checkout.payment.gateway.service.execution;

import com.checkout.payment.gateway.model.CreatePaymentRequest;

public interface PaymentExecutionStrategy {

  PaymentExecutionResult execute(String idempotencyKey, CreatePaymentRequest request);
}
