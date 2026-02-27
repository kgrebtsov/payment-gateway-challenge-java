package com.checkout.payment.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentResult;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankClientException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.PaymentRecord;
import com.checkout.payment.gateway.repository.InMemoryPaymentsRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  private static final String PAYMENTS_PATH = "/payments";
  private static final int DEFAULT_EXPIRY_MONTH = 12;
  private static final int DEFAULT_EXPIRY_YEAR = 2026;
  private static final String DEFAULT_CURRENCY = "GBP";
  private static final String DEFAULT_CVV = "123";
  private static final int DEFAULT_AMOUNT = 100;

  @Autowired
  private MockMvc mvc;

  @Autowired
  private InMemoryPaymentsRepository paymentsRepository;

  @MockBean
  private BankClient bankClient;

  @Test
  void invalidRequestReturns400AndDoesNotCallBank() throws Exception {
    String invalidPayload = """
        {
          "cardNumber": "4111abcd",
          "expiryMonth": 12,
          "expiryYear": 2026,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """;

    createPaymentWithIdempotency("invalid-key", invalidPayload)
        .andExpect(status().isBadRequest())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors[0].field").value("cardNumber"));

    verify(bankClient, times(0)).authorize(any());
  }

  @Test
  void cardNumberShorterThan14DigitsReturns400AndDoesNotCallBank() throws Exception {
    String invalidPayload = """
        {
          "cardNumber": "4111111111111",
          "expiryMonth": 12,
          "expiryYear": 2026,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """;

    createPaymentWithIdempotency("invalid-length-key", invalidPayload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors[0].field").value("cardNumber"));

    verify(bankClient, times(0)).authorize(any());
  }

  @Test
  void validRequestWithoutIdempotencyHeaderIsAccepted() throws Exception {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResult(true));

    createPayment(payload("4111111111111111"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.status").value("Authorized"));

    verify(bankClient, times(1)).authorize(any());
  }

  @Test
  void blankIdempotencyHeaderIsTreatedAsNoIdempotency() throws Exception {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResult(true));

    String payload = payload("4111111111111111");

    MvcResult first = createPaymentWithIdempotency("", payload)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andReturn();

    MvcResult second = createPaymentWithIdempotency("   ", payload)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andReturn();

    String firstPaymentId = extractPaymentId(first.getResponse().getContentAsString());
    String secondPaymentId = extractPaymentId(second.getResponse().getContentAsString());
    assertNotEquals(firstPaymentId, secondPaymentId);
    verify(bankClient, times(2)).authorize(any());
  }

  @Test
  void numericNonLuhnCardNumberIsAcceptedPerChallengeRules() throws Exception {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResult(false));

    createPaymentWithIdempotency("non-luhn-ok", payload("4111111111111112"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"));

    verify(bankClient, times(1)).authorize(any());
  }

  @Test
  void bankUnavailableIsMappedTo503() throws Exception {
    assertBankErrorMapping(
        new BankUnavailableException("Bank simulator unavailable"),
        "bank-503",
        status().isServiceUnavailable(),
        "Bank simulator unavailable");
  }

  @Test
  void bankClientFailureIsMappedTo502() throws Exception {
    assertBankErrorMapping(
        new BankClientException("Bank simulator error"),
        "bank-502",
        status().isBadGateway(),
        "Bank simulator error");
  }

  @Test
  void idempotencySamePayloadReplaysSameResponseAndDifferentPayloadReturns409() throws Exception {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResult(true));

    String payload1 = payload("4111111111111111");
    String payload2 = payload("4111111111111111", 200);

    MvcResult first = createPaymentWithHeaders("idempo-1", "req-1", payload1)
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "req-1"))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.maskedCardNumber").value("**** **** **** 1111"))
        .andReturn();

    String firstBody = first.getResponse().getContentAsString();
    String paymentId = extractPaymentId(firstBody);

    createPaymentWithIdempotency("idempo-1", payload1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentId").value(paymentId))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.maskedCardNumber").value("**** **** **** 1111"));

    createPaymentWithIdempotency("idempo-1", payload2)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));

    verify(bankClient, times(1)).authorize(any());
  }

  @Test
  void getPaymentReturnsPaymentRecord() throws Exception {
    UUID paymentId = UUID.randomUUID();
    paymentsRepository.save(new PaymentRecord(
        paymentId,
        PaymentStatus.AUTHORIZED,
        150,
        "EUR",
        "4242",
        12,
        2027));

    mvc.perform(get("/payments/{paymentId}", paymentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.amount").value(150))
        .andExpect(jsonPath("$.cardLast4").value("4242"))
        .andExpect(jsonPath("$.maskedCardNumber").value("**** **** **** 4242"));
  }

  @Test
  void getPaymentReturns404WhenNotFound() throws Exception {
    mvc.perform(get("/payments/{paymentId}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].field").value("paymentId"))
        .andExpect(jsonPath("$.errors[0].message").value("Payment not found"));
  }

  private String extractPaymentId(String json) {
    String marker = "\"paymentId\":\"";
    int start = json.indexOf(marker);
    int from = start + marker.length();
    int end = json.indexOf('"', from);
    return json.substring(from, end);
  }

  private String payload(String cardNumber) {
    return payload(cardNumber, DEFAULT_AMOUNT);
  }

  private String payload(String cardNumber, int amount) {
    return """
        {
          "cardNumber": "%s",
          "expiryMonth": %d,
          "expiryYear": %d,
          "currency": "%s",
          "amount": %d,
          "cvv": "%s"
        }
        """.formatted(
        cardNumber,
        DEFAULT_EXPIRY_MONTH,
        DEFAULT_EXPIRY_YEAR,
        DEFAULT_CURRENCY,
        amount,
        DEFAULT_CVV);
  }

  private ResultActions createPayment(String payload) throws Exception {
    return mvc.perform(post(PAYMENTS_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload));
  }

  private ResultActions createPaymentWithIdempotency(String idempotencyKey, String payload)
      throws Exception {
    return mvc.perform(post(PAYMENTS_PATH)
        .header("Idempotency-Key", idempotencyKey)
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload));
  }

  private ResultActions createPaymentWithHeaders(String idempotencyKey, String requestId,
      String payload) throws Exception {
    return mvc.perform(post(PAYMENTS_PATH)
        .header("Idempotency-Key", idempotencyKey)
        .header("X-Request-Id", requestId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload));
  }

  private void assertBankErrorMapping(
      RuntimeException bankException,
      String idempotencyKey,
      ResultMatcher expectedStatus,
      String expectedMessage) throws Exception {
    when(bankClient.authorize(any())).thenThrow(bankException);

    createPaymentWithIdempotency(idempotencyKey, payload("4111111111111111"))
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.errors[0].field").value("bank"))
        .andExpect(jsonPath("$.errors[0].message").value(expectedMessage));

    verify(bankClient, times(1)).authorize(any());
  }
}
