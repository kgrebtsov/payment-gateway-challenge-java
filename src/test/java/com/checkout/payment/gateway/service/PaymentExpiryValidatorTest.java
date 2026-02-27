package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.checkout.payment.gateway.exception.InvalidPaymentRequestException;
import com.checkout.payment.gateway.service.creation.PaymentExpiryValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentExpiryValidatorTest {

  private PaymentExpiryValidator validator;

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(Instant.parse("2026-02-24T12:00:00Z"), ZoneOffset.UTC);
    validator = new PaymentExpiryValidator(fixedClock);
  }

  @Test
  void acceptsFutureExpiry() {
    assertDoesNotThrow(() -> validator.validateFutureExpiry(3, 2026));
    assertDoesNotThrow(() -> validator.validateFutureExpiry(1, 2027));
  }

  @Test
  void rejectsCurrentMonthExpiry() {
    InvalidPaymentRequestException ex = assertThrows(InvalidPaymentRequestException.class,
        () -> validator.validateFutureExpiry(2, 2026));
    assertEquals("expiryMonth", ex.getField());
    assertEquals("Expiry date must be in the future", ex.getMessage());
  }

  @Test
  void rejectsPastExpiry() {
    assertThrows(InvalidPaymentRequestException.class,
        () -> validator.validateFutureExpiry(1, 2026));
  }

  @Test
  void ignoresNullValues() {
    assertDoesNotThrow(() -> validator.validateFutureExpiry(null, 2026));
    assertDoesNotThrow(() -> validator.validateFutureExpiry(12, null));
  }
}
