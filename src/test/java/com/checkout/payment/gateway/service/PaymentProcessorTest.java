package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentRequest;
import com.checkout.payment.gateway.client.BankPaymentResult;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.service.creation.PaymentDecision;
import com.checkout.payment.gateway.service.creation.PaymentProcessor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PaymentProcessorTest {

  private final BankClient bankClient = Mockito.mock(BankClient.class);
  private SimpleMeterRegistry meterRegistry;
  private PaymentProcessor paymentProcessor;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    paymentProcessor = new PaymentProcessor(bankClient, meterRegistry);
  }

  @Test
  void mapsAuthorizedBankResultAndFormatsBankRequest() {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResult(true));
    CreatePaymentRequest request = new CreatePaymentRequest(
        "4111111111111111", 3, 2027, "USD", 250, "123");

    PaymentDecision decision = paymentProcessor.process(request);

    ArgumentCaptor<BankPaymentRequest> captor = ArgumentCaptor.forClass(BankPaymentRequest.class);
    verify(bankClient).authorize(captor.capture());
    BankPaymentRequest bankRequest = captor.getValue();

    assertEquals("4111111111111111", bankRequest.cardNumber());
    assertEquals("03/2027", bankRequest.expiryDate());
    assertEquals("USD", bankRequest.currency());
    assertEquals(250, bankRequest.amount());
    assertEquals("123", bankRequest.cvv());

    assertEquals(PaymentStatus.AUTHORIZED, decision.status());
    assertEquals(1.0, meterRegistry.get("payment_success_total").counter().count());
    assertEquals(0.0, meterRegistry.get("payment_declined_total").counter().count());
  }

  @Test
  void mapsDeclinedBankResult() {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResult(false));

    PaymentDecision decision = paymentProcessor.process(new CreatePaymentRequest(
        "4000000000000002", 12, 2026, "GBP", 100, "999"));

    assertEquals(PaymentStatus.DECLINED, decision.status());
    assertEquals(0.0, meterRegistry.get("payment_success_total").counter().count());
    assertEquals(1.0, meterRegistry.get("payment_declined_total").counter().count());
  }
}
