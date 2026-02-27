package com.checkout.payment.gateway.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PaymentRecord;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryPaymentsRepositoryTest {

  private InMemoryPaymentsRepository repository;

  @BeforeEach
  void setUp() {
    repository = new InMemoryPaymentsRepository();
  }

  @Test
  void savesAndReturnsPaymentById() {
    PaymentRecord payment = new PaymentRecord(
        UUID.randomUUID(), PaymentStatus.AUTHORIZED, 100, "GBP", "1111", 12, 2026);

    repository.save(payment);

    assertTrue(repository.findById(payment.paymentId()).isPresent());
    assertEquals(payment, repository.findById(payment.paymentId()).orElseThrow());
  }

  @Test
  void returnsEmptyWhenPaymentNotFound() {
    assertFalse(repository.findById(UUID.randomUUID()).isPresent());
  }
}
