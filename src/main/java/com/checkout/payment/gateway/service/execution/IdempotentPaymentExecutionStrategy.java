package com.checkout.payment.gateway.service.execution;

import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.model.PaymentRecord;
import com.checkout.payment.gateway.service.creation.PaymentCreationWorkflow;
import com.checkout.payment.gateway.service.idempotency.PaymentIdempotencyExecutor;
import org.springframework.stereotype.Component;

@Component
public class IdempotentPaymentExecutionStrategy implements PaymentExecutionStrategy {

  private final PaymentIdempotencyExecutor paymentIdempotencyExecutor;
  private final PaymentCreationWorkflow paymentCreationWorkflow;

  public IdempotentPaymentExecutionStrategy(
      PaymentIdempotencyExecutor paymentIdempotencyExecutor,
      PaymentCreationWorkflow paymentCreationWorkflow) {
    this.paymentIdempotencyExecutor = paymentIdempotencyExecutor;
    this.paymentCreationWorkflow = paymentCreationWorkflow;
  }

  @Override
  public PaymentExecutionResult execute(String idempotencyKey, CreatePaymentRequest request) {
    PaymentIdempotencyExecutor.ExecutionResult executionResult = paymentIdempotencyExecutor.execute(
        idempotencyKey,
        request,
        () -> paymentCreationWorkflow.create(idempotencyKey, request));
    PaymentRecord payment = executionResult.payment();
    return new PaymentExecutionResult(payment, executionResult.replayed());
  }
}
