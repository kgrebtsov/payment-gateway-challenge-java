package com.checkout.payment.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentIdempotencyConcurrencyIntegrationTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private BankClient bankClient;

  private ExecutorService executorService;

  @BeforeEach
  void setUp() {
    executorService = Executors.newFixedThreadPool(2);
  }

  @AfterEach
  void tearDown() {
    executorService.shutdownNow();
  }

  @Test
  void concurrentRequestsWithSameIdempotencyKeyTriggerSingleBankCall() throws Exception {
    when(bankClient.authorize(any())).thenAnswer(invocation -> {
      TimeUnit.MILLISECONDS.sleep(250);
      return new BankPaymentResult(true);
    });

    String idempotencyKey = "it-" + UUID.randomUUID();
    String payload = """
        {
          "cardNumber": "4111111111111111",
          "expiryMonth": 12,
          "expiryYear": 2027,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """;

    CyclicBarrier barrier = new CyclicBarrier(2);
    Callable<ResponseEntity<String>> requestTask = () -> {
      barrier.await(2, TimeUnit.SECONDS);
      return postPayment(idempotencyKey, payload);
    };

    Future<ResponseEntity<String>> firstFuture = executorService.submit(requestTask);
    Future<ResponseEntity<String>> secondFuture = executorService.submit(requestTask);

    ResponseEntity<String> first = firstFuture.get(5, TimeUnit.SECONDS);
    ResponseEntity<String> second = secondFuture.get(5, TimeUnit.SECONDS);

    assertEquals(200, first.getStatusCode().value());
    assertEquals(200, second.getStatusCode().value());

    JsonNode firstJson = objectMapper.readTree(first.getBody());
    JsonNode secondJson = objectMapper.readTree(second.getBody());

    assertEquals(firstJson.get("paymentId").asText(), secondJson.get("paymentId").asText());
    assertEquals(firstJson.get("status").asText(), secondJson.get("status").asText());
    assertEquals("Authorized", firstJson.get("status").asText());
    assertFalse(firstJson.get("paymentId").asText().isEmpty());

    verify(bankClient, times(1)).authorize(any());
  }

  private ResponseEntity<String> postPayment(String idempotencyKey, String payload) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Idempotency-Key", idempotencyKey);
    headers.set("X-Request-Id", "req-" + UUID.randomUUID());
    return testRestTemplate.postForEntity(
        "http://localhost:" + port + "/payments",
        new HttpEntity<>(payload, headers),
        String.class);
  }
}
