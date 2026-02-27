package com.checkout.payment.gateway.client;

public interface BankClient {

  BankPaymentResult authorize(BankPaymentRequest request);
}
