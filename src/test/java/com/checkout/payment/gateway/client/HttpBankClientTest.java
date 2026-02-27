package com.checkout.payment.gateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.checkout.payment.gateway.configuration.BankClientProperties;
import com.checkout.payment.gateway.exception.BankClientException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;

class HttpBankClientTest {

  private RestTemplate restTemplate;
  private MockRestServiceServer mockServer;
  private HttpBankClient httpBankClient;
  private SimpleMeterRegistry meterRegistry;
  private BankClientErrorTranslator errorTranslator;
  private BankClientProperties bankClientProperties;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    errorTranslator = new BankClientErrorTranslator();
    bankClientProperties = new BankClientProperties("http://localhost:8080", "/payments");
    restTemplate = new RestTemplate();
    mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    httpBankClient = new HttpBankClient(restTemplate, errorTranslator, meterRegistry, bankClientProperties);
  }

  @Test
  void mapsAuthorizedSuccessResponseAndSendsExpectedBankRequestPayload() {
    mockServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {
              "card_number": "4111111111111111",
              "expiry_date": "12/2027",
              "currency": "GBP",
              "amount": 100,
              "cvv": "123"
            }
            """))
        .andRespond(withSuccess("""
            {
              "authorized": true,
              "authorization_code": "auth-123"
            }
            """, MediaType.APPLICATION_JSON));

    BankPaymentResult result = httpBankClient.authorize(sampleRequest());

    assertEquals(true, result.authorized());
    mockServer.verify();
    assertEquals(0.0, meterRegistry.get("payment_bank_errors_total").counter().count());
    assertEquals(1L, meterRegistry.get("bank_call_latency").timer().count());
  }

  @Test
  void mapsDeclinedSuccessResponse() {
    mockServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("""
            {
              "authorized": false,
              "authorization_code": null
            }
            """, MediaType.APPLICATION_JSON));

    BankPaymentResult result = httpBankClient.authorize(sampleRequest());

    assertEquals(false, result.authorized());
    mockServer.verify();
    assertEquals(0.0, meterRegistry.get("payment_bank_errors_total").counter().count());
    assertEquals(1L, meterRegistry.get("bank_call_latency").timer().count());
  }

  @Test
  void maps503ToBankUnavailableException() {
    mockServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertThrows(BankUnavailableException.class, () -> httpBankClient.authorize(sampleRequest()));
    mockServer.verify();
    assertEquals(1.0, meterRegistry.get("payment_bank_errors_total").counter().count());
    assertEquals(1L, meterRegistry.get("bank_call_latency").timer().count());
  }

  @Test
  void maps502ToBankUnavailableException() {
    mockServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

    assertThrows(BankUnavailableException.class, () -> httpBankClient.authorize(sampleRequest()));
    mockServer.verify();
    assertEquals(1.0, meterRegistry.get("payment_bank_errors_total").counter().count());
    assertEquals(1L, meterRegistry.get("bank_call_latency").timer().count());
  }

  @Test
  void mapsTimeoutToBankUnavailableException() {
    ResourceAccessException cause = new ResourceAccessException("timeout");
    HttpBankClient timeoutClient = new HttpBankClient(new RestTemplate() {
      @Override
      public <T> org.springframework.http.ResponseEntity<T> exchange(
          String url, HttpMethod method, org.springframework.http.HttpEntity<?> requestEntity,
          Class<T> responseType, Object... uriVariables) {
        throw cause;
      }
    }, errorTranslator, meterRegistry, bankClientProperties);

    BankUnavailableException ex =
        assertThrows(BankUnavailableException.class, () -> timeoutClient.authorize(sampleRequest()));
    assertSame(cause, ex.getCause());
    assertEquals(1.0, meterRegistry.get("payment_bank_errors_total").counter().count());
    assertEquals(1L, meterRegistry.get("bank_call_latency").timer().count());
  }

  @Test
  void mapsOtherBankErrorsToBankClientException() {
    mockServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThrows(BankClientException.class, () -> httpBankClient.authorize(sampleRequest()));
    mockServer.verify();
    assertEquals(1.0, meterRegistry.get("payment_bank_errors_total").counter().count());
    assertEquals(1L, meterRegistry.get("bank_call_latency").timer().count());
  }

  @Test
  void mapsMalformedSuccessfulResponseToBankClientException() {
    mockServer.expect(once(), requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThrows(BankClientException.class, () -> httpBankClient.authorize(sampleRequest()));
    mockServer.verify();
    assertEquals(1.0, meterRegistry.get("payment_bank_errors_total").counter().count());
    assertEquals(1L, meterRegistry.get("bank_call_latency").timer().count());
  }

  private BankPaymentRequest sampleRequest() {
    return new BankPaymentRequest("4111111111111111", "12/2027", "GBP", 100, "123");
  }
}
