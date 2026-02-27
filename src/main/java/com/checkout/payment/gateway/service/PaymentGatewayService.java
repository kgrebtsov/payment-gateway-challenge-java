package com.checkout.payment.gateway.service;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.model.PaymentRecord;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.InMemoryPaymentsRepository;
import com.checkout.payment.gateway.service.execution.DirectPaymentExecutionStrategy;
import com.checkout.payment.gateway.service.execution.IdempotentPaymentExecutionStrategy;
import com.checkout.payment.gateway.service.execution.PaymentExecutionResult;
import com.checkout.payment.gateway.service.execution.PaymentExecutionStrategy;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final InMemoryPaymentsRepository paymentsRepository;
  private final DirectPaymentExecutionStrategy directPaymentExecutionStrategy;
  private final IdempotentPaymentExecutionStrategy idempotentPaymentExecutionStrategy;

  public PaymentGatewayService(
      InMemoryPaymentsRepository paymentsRepository,
      DirectPaymentExecutionStrategy directPaymentExecutionStrategy,
      IdempotentPaymentExecutionStrategy idempotentPaymentExecutionStrategy) {
    this.paymentsRepository = paymentsRepository;
    this.directPaymentExecutionStrategy = directPaymentExecutionStrategy;
    this.idempotentPaymentExecutionStrategy = idempotentPaymentExecutionStrategy;
  }

  public PaymentResponse getPaymentById(UUID id) {
    return paymentsRepository.findById(id)
        .map(PaymentResponse::from)
        .orElseThrow(() -> new PaymentNotFoundException("Payment not found"));
  }

  public PaymentResponse createPayment(String idempotencyKey, CreatePaymentRequest request) {
    PaymentExecutionResult executionResult =
        selectExecutionStrategy(idempotencyKey).execute(idempotencyKey, request);
    PaymentRecord payment = executionResult.payment();
    logFinalOutcome(idempotencyKey, payment, executionResult.replayed());
    return PaymentResponse.from(payment);
  }

  private PaymentExecutionStrategy selectExecutionStrategy(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return directPaymentExecutionStrategy;
    }
    return idempotentPaymentExecutionStrategy;
  }

  private void logFinalOutcome(String idempotencyKey, PaymentRecord payment, boolean replayed) {
    LOG.info("payment.final.outcome",
        keyValue("event", "payment.final.outcome"),
        keyValue("idempotencyKey", loggableIdempotencyKey(idempotencyKey)),
        keyValue("paymentId", payment.paymentId()),
        keyValue("status", payment.status()),
        keyValue("replay", replayed));
  }

  private String loggableIdempotencyKey(String idempotencyKey) {
    return (idempotencyKey == null || idempotencyKey.isBlank()) ? "-" : idempotencyKey;
  }
}
