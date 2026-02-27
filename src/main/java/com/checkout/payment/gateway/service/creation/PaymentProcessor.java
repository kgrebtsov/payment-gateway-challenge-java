package com.checkout.payment.gateway.service.creation;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentRequest;
import com.checkout.payment.gateway.client.BankPaymentResult;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.CreatePaymentRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentProcessor.class);
  private static final String PAYMENT_SUCCESS_TOTAL_METRIC = "payment_success_total";
  private static final String PAYMENT_DECLINED_TOTAL_METRIC = "payment_declined_total";

  private final BankClient bankClient;
  private final Counter paymentSuccessCounter;
  private final Counter paymentDeclinedCounter;

  public PaymentProcessor(BankClient bankClient, MeterRegistry meterRegistry) {
    this.bankClient = bankClient;
    this.paymentSuccessCounter = meterRegistry.counter(PAYMENT_SUCCESS_TOTAL_METRIC);
    this.paymentDeclinedCounter = meterRegistry.counter(PAYMENT_DECLINED_TOTAL_METRIC);
  }

  public PaymentDecision process(CreatePaymentRequest request) {
    BankPaymentResult bankResult = bankClient.authorize(toBankPaymentRequest(request));
    PaymentStatus status = toPaymentStatus(bankResult);
    recordMetrics(status);
    LOG.info("payment.bank.result",
        keyValue("event", "payment.bank.result"),
        keyValue("status", status));
    return new PaymentDecision(status);
  }

  private BankPaymentRequest toBankPaymentRequest(CreatePaymentRequest request) {
    return new BankPaymentRequest(
        request.cardNumber(),
        String.format("%02d/%04d", request.expiryMonth(), request.expiryYear()),
        request.currency(),
        request.amount(),
        request.cvv());
  }

  private PaymentStatus toPaymentStatus(BankPaymentResult bankResult) {
    return bankResult.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
  }

  private void recordMetrics(PaymentStatus status) {
    if (status == PaymentStatus.AUTHORIZED) {
      paymentSuccessCounter.increment();
      return;
    }
    paymentDeclinedCounter.increment();
  }
}
