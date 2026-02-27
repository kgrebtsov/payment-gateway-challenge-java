package com.checkout.payment.gateway.service.execution;

import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.model.PaymentRecord;
import com.checkout.payment.gateway.service.creation.PaymentCreationWorkflow;
import org.springframework.stereotype.Component;

@Component
public class DirectPaymentExecutionStrategy implements PaymentExecutionStrategy {

  private final PaymentCreationWorkflow paymentCreationWorkflow;

  public DirectPaymentExecutionStrategy(PaymentCreationWorkflow paymentCreationWorkflow) {
    this.paymentCreationWorkflow = paymentCreationWorkflow;
  }

  @Override
  public PaymentExecutionResult execute(String idempotencyKey, CreatePaymentRequest request) {
    PaymentRecord payment = paymentCreationWorkflow.create(idempotencyKey, request);
    return new PaymentExecutionResult(payment, false);
  }
}
