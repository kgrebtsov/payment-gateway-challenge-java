package com.checkout.payment.gateway.service.idempotency;

import com.checkout.payment.gateway.exception.IdempotencyConflictException;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.model.PaymentRecord;
import com.checkout.payment.gateway.repository.InMemoryIdempotencyStore;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentIdempotencyExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentIdempotencyExecutor.class);

  private final InMemoryIdempotencyStore idempotencyStore;
  private final PaymentRequestFingerprintGenerator paymentRequestFingerprintGenerator;

  public PaymentIdempotencyExecutor(
      InMemoryIdempotencyStore idempotencyStore,
      PaymentRequestFingerprintGenerator paymentRequestFingerprintGenerator) {
    this.idempotencyStore = idempotencyStore;
    this.paymentRequestFingerprintGenerator = paymentRequestFingerprintGenerator;
  }

  public ExecutionResult execute(
      String idempotencyKey,
      CreatePaymentRequest request,
      Supplier<PaymentRecord> ownerComputation) {
    InMemoryIdempotencyStore.Reservation reservation = reserveOrThrowConflict(idempotencyKey, request);
    if (!reservation.owner()) {
      LOG.info("payment.idempotent.replay",
          keyValue("event", "payment.idempotent.replay"),
          keyValue("idempotencyKey", idempotencyKey));
      return new ExecutionResult(awaitReservationResult(reservation), true);
    }

    try {
      PaymentRecord created = ownerComputation.get();
      reservation.future().complete(created);
      return new ExecutionResult(created, false);
    } catch (RuntimeException ex) {
      failReservation(idempotencyKey, reservation, ex);
      throw ex;
    }
  }

  private InMemoryIdempotencyStore.Reservation reserveOrThrowConflict(
      String idempotencyKey, CreatePaymentRequest request) {
    try {
      return idempotencyStore.reserve(
          idempotencyKey,
          paymentRequestFingerprintGenerator.generate(request));
    } catch (InMemoryIdempotencyStore.PayloadConflictException ex) {
      throw new IdempotencyConflictException("Key already used with different payload");
    }
  }

  private void failReservation(
      String idempotencyKey,
      InMemoryIdempotencyStore.Reservation reservation,
      RuntimeException ex) {
    reservation.future().completeExceptionally(ex);
    idempotencyStore.remove(idempotencyKey, reservation);
  }

  private PaymentRecord awaitReservationResult(InMemoryIdempotencyStore.Reservation reservation) {
    try {
      return reservation.future().join();
    } catch (CompletionException ex) {
      if (ex.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw ex;
    }
  }

  public record ExecutionResult(PaymentRecord payment, boolean replayed) {
  }
}
