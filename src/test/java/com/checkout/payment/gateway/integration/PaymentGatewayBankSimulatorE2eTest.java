package com.checkout.payment.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "bank.simulator.base-url=http://localhost:8080")
class PaymentGatewayBankSimulatorE2eTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeAll
  static void assumeBankSimulatorRunning() {
    Assumptions.assumeTrue(isTcpPortOpen("localhost", 8080),
        "Bank simulator is not running on localhost:8080. Start it with docker-compose up");
  }

  @Test
  void oddEndingCardIsAuthorizedBySimulatorThroughGateway() throws Exception {
    ResponseEntity<String> response = postPayment("4111111111111111", 100);

    assertEquals(200, response.getStatusCode().value());
    JsonNode json = objectMapper.readTree(response.getBody());
    assertEquals("Authorized", json.get("status").asText());
    assertEquals("1111", json.get("cardLast4").asText());
    assertEquals("**** **** **** 1111", json.get("maskedCardNumber").asText());
    assertEquals("GBP", json.get("currency").asText());
    assertEquals(100, json.get("amount").asInt());
    assertTrue(json.hasNonNull("paymentId"));
  }

  @Test
  void evenEndingCardIsDeclinedBySimulatorThroughGateway() throws Exception {
    ResponseEntity<String> response = postPayment("4000000000000002", 250);

    assertEquals(200, response.getStatusCode().value());
    JsonNode json = objectMapper.readTree(response.getBody());
    assertEquals("Declined", json.get("status").asText());
    assertEquals("0002", json.get("cardLast4").asText());
    assertEquals("**** **** **** 0002", json.get("maskedCardNumber").asText());
    assertEquals(250, json.get("amount").asInt());
  }

  @Test
  void zeroEndingCardReturnsServiceUnavailableFromGateway() throws Exception {
    ResponseEntity<String> response = postPayment("4000000000000000", 100);

    assertEquals(503, response.getStatusCode().value());
    JsonNode json = objectMapper.readTree(response.getBody());
    assertEquals("bank", json.get("errors").get(0).get("field").asText());
    assertEquals("Bank simulator unavailable", json.get("errors").get(0).get("message").asText());
  }

  private ResponseEntity<String> postPayment(String cardNumber, int amount) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Idempotency-Key", "e2e-" + UUID.randomUUID());
    headers.set("X-Request-Id", "req-" + UUID.randomUUID());
    return testRestTemplate.postForEntity(
        "http://localhost:" + port + "/payments",
        new HttpEntity<>(payload(cardNumber, amount), headers),
        String.class);
  }

  private String payload(String cardNumber, int amount) {
    return """
        {
          "cardNumber": "%s",
          "expiryMonth": 12,
          "expiryYear": 2030,
          "currency": "GBP",
          "amount": %d,
          "cvv": "123"
        }
        """.formatted(cardNumber, amount);
  }

  private static boolean isTcpPortOpen(String host, int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 500);
      return true;
    } catch (IOException ex) {
      return false;
    }
  }
}
