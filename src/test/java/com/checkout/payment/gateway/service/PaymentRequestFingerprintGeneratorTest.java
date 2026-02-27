package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.service.idempotency.PaymentRequestFingerprintGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentRequestFingerprintGeneratorTest {

  private PaymentRequestFingerprintGenerator fingerprintGenerator;

  @BeforeEach
  void setUp() {
    fingerprintGenerator = new PaymentRequestFingerprintGenerator();
  }

  @Test
  void returnsSameFingerprintForIdenticalPayload() {
    CreatePaymentRequest request = request("4111111111111111", "GBP", 100, "123");

    String first = fingerprintGenerator.generate(request);
    String second = fingerprintGenerator.generate(request);

    assertEquals(first, second);
  }

  @Test
  void returnsDifferentFingerprintForDifferentPayload() {
    CreatePaymentRequest first = request("4111111111111111", "GBP", 100, "123");
    CreatePaymentRequest second = request("4111111111111111", "GBP", 200, "123");

    assertNotEquals(fingerprintGenerator.generate(first), fingerprintGenerator.generate(second));
  }

  @Test
  void normalizesCurrencyCaseInFingerprint() {
    CreatePaymentRequest upper = request("4111111111111111", "GBP", 100, "123");
    CreatePaymentRequest lower = request("4111111111111111", "gbp", 100, "123");

    assertEquals(fingerprintGenerator.generate(upper), fingerprintGenerator.generate(lower));
  }

  private CreatePaymentRequest request(String cardNumber, String currency, int amount, String cvv) {
    return new CreatePaymentRequest(cardNumber, 12, 2026, currency, amount, cvv);
  }
}
