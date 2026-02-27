package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.configuration.BankClientProperties;
import com.checkout.payment.gateway.exception.BankClientException;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpBankClient implements BankClient {

  private static final Logger LOG = LoggerFactory.getLogger(HttpBankClient.class);
  private static final String PAYMENT_BANK_ERRORS_TOTAL_METRIC = "payment_bank_errors_total";
  private static final String BANK_CALL_LATENCY_METRIC = "bank_call_latency";

  private final RestTemplate restTemplate;
  private final String bankPaymentsUrl;
  private final BankClientErrorTranslator errorTranslator;
  private final Counter paymentBankErrorsCounter;
  private final Timer bankCallLatencyTimer;

  public HttpBankClient(
      RestTemplate restTemplate,
      BankClientErrorTranslator errorTranslator,
      MeterRegistry meterRegistry,
      BankClientProperties bankClientProperties) {
    this.restTemplate = restTemplate;
    this.bankPaymentsUrl = buildPaymentsUrl(bankClientProperties);
    this.errorTranslator = errorTranslator;
    this.paymentBankErrorsCounter = meterRegistry.counter(PAYMENT_BANK_ERRORS_TOTAL_METRIC);
    this.bankCallLatencyTimer = meterRegistry.timer(BANK_CALL_LATENCY_METRIC);
  }

  @Override
  public BankPaymentResult authorize(BankPaymentRequest request) {
    Timer.Sample sample = Timer.start();
    try {
      ResponseEntity<BankPaymentResponse> response = restTemplate.exchange(
          bankPaymentsUrl,
          HttpMethod.POST,
          new HttpEntity<>(request),
          BankPaymentResponse.class);
      return mapSuccessResponse(response);
    } catch (RestClientException ex) {
      paymentBankErrorsCounter.increment();
      throw errorTranslator.translate(ex);
    } finally {
      sample.stop(bankCallLatencyTimer);
    }
  }

  private BankPaymentResult mapSuccessResponse(ResponseEntity<BankPaymentResponse> response) {
    BankPaymentResponse body = response.getBody();
    if (body == null || body.authorized() == null) {
      paymentBankErrorsCounter.increment();
      LOG.warn("bank.call.malformed_response",
          keyValue("event", "bank.call.malformed_response"),
          keyValue("httpStatus", response.getStatusCode().value()));
      throw new BankClientException("Bank simulator returned malformed response");
    }

    boolean authorized = body.authorized();
    LOG.info("bank.call.result",
        keyValue("event", "bank.call.result"),
        keyValue("httpStatus", response.getStatusCode().value()),
        keyValue("authorized", authorized));
    return new BankPaymentResult(authorized);
  }

  private String buildPaymentsUrl(BankClientProperties bankClientProperties) {
    String baseUrl = stripTrailingSlash(bankClientProperties.baseUrl());
    String paymentsPath = ensureLeadingSlash(bankClientProperties.paymentsPath());
    return baseUrl + paymentsPath;
  }

  private String stripTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private String ensureLeadingSlash(String value) {
    return value.startsWith("/") ? value : "/" + value;
  }

}
