package com.checkout.payment.gateway.service.creation;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.model.PaymentRecord;
import com.checkout.payment.gateway.repository.InMemoryPaymentsRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentCreationWorkflow {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentCreationWorkflow.class);
  private static final int CARD_LAST4_LENGTH = 4;

  private final PaymentExpiryValidator paymentExpiryValidator;
  private final PaymentProcessor paymentProcessor;
  private final InMemoryPaymentsRepository paymentsRepository;

  public PaymentCreationWorkflow(
      PaymentExpiryValidator paymentExpiryValidator,
      PaymentProcessor paymentProcessor,
      InMemoryPaymentsRepository paymentsRepository) {
    this.paymentExpiryValidator = paymentExpiryValidator;
    this.paymentProcessor = paymentProcessor;
    this.paymentsRepository = paymentsRepository;
  }

  public PaymentRecord create(String idempotencyKey, CreatePaymentRequest request) {
    paymentExpiryValidator.validateFutureExpiry(request.expiryMonth(), request.expiryYear());

    LOG.info("payment.start",
        keyValue("event", "payment.start"),
        keyValue("idempotencyKey", loggableIdempotencyKey(idempotencyKey)),
        keyValue("amount", request.amount()),
        keyValue("currency", request.currency()));
    PaymentDecision decision = paymentProcessor.process(request);

    PaymentRecord payment = new PaymentRecord(
        UUID.randomUUID(),
        decision.status(),
        request.amount(),
        request.currency(),
        extractCardLast4(request.cardNumber()),
        request.expiryMonth(),
        request.expiryYear());

    paymentsRepository.save(payment);
    return payment;
  }

  private static String loggableIdempotencyKey(String idempotencyKey) {
    return (idempotencyKey == null || idempotencyKey.isBlank()) ? "-" : idempotencyKey;
  }

  private static String extractCardLast4(String cardNumber) {
    if (cardNumber == null || cardNumber.length() < CARD_LAST4_LENGTH) {
      throw new IllegalArgumentException("Card number must contain at least 4 digits");
    }
    return cardNumber.substring(cardNumber.length() - CARD_LAST4_LENGTH);
  }
}
