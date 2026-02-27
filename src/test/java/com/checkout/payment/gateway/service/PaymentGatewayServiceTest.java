package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentResult;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.IdempotencyConflictException;
import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.InMemoryIdempotencyStore;
import com.checkout.payment.gateway.repository.InMemoryPaymentsRepository;
import com.checkout.payment.gateway.service.creation.PaymentCreationWorkflow;
import com.checkout.payment.gateway.service.creation.PaymentExpiryValidator;
import com.checkout.payment.gateway.service.creation.PaymentProcessor;
import com.checkout.payment.gateway.service.execution.DirectPaymentExecutionStrategy;
import com.checkout.payment.gateway.service.execution.IdempotentPaymentExecutionStrategy;
import com.checkout.payment.gateway.service.idempotency.PaymentIdempotencyExecutor;
import com.checkout.payment.gateway.service.idempotency.PaymentRequestFingerprintGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentGatewayServiceTest {

  private final BankClient bankClient = Mockito.mock(BankClient.class);
  private PaymentGatewayService service;
  private ExecutorService executorService;

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(Instant.parse("2026-02-24T12:00:00Z"), ZoneOffset.UTC);
    InMemoryIdempotencyStore idempotencyStore = new InMemoryIdempotencyStore();
    InMemoryPaymentsRepository paymentsRepository = new InMemoryPaymentsRepository();
    PaymentRequestFingerprintGenerator fingerprintGenerator = new PaymentRequestFingerprintGenerator();
    PaymentExpiryValidator paymentExpiryValidator = new PaymentExpiryValidator(fixedClock);
    PaymentProcessor paymentProcessor = new PaymentProcessor(bankClient, new SimpleMeterRegistry());
    PaymentCreationWorkflow paymentCreationWorkflow =
        new PaymentCreationWorkflow(paymentExpiryValidator, paymentProcessor, paymentsRepository);
    service = new PaymentGatewayService(
        paymentsRepository,
        new DirectPaymentExecutionStrategy(paymentCreationWorkflow),
        new IdempotentPaymentExecutionStrategy(
            new PaymentIdempotencyExecutor(idempotencyStore, fingerprintGenerator),
            paymentCreationWorkflow));
    executorService = Executors.newFixedThreadPool(2);
  }

  @AfterEach
  void tearDown() {
    executorService.shutdownNow();
  }

  @Test
  void createsAuthorizedPaymentWhenBankAuthorizesOddCardScenario() {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResult(true));

    PaymentResponse response = service.createPayment("k-1", validRequest("4111111111111111"));

    assertEquals(PaymentStatus.AUTHORIZED, response.status());
    assertEquals("**** **** **** 1111", response.maskedCardNumber());
    assertEquals("1111", response.cardLast4());
    verify(bankClient, times(1)).authorize(any());
  }

  @Test
  void createsDeclinedPaymentWhenBankDeclinesEvenCardScenario() {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResult(false));

    PaymentResponse response = service.createPayment("k-2", validRequest("4000000000000002"));

    assertEquals(PaymentStatus.DECLINED, response.status());
    assertEquals("**** **** **** 0002", response.maskedCardNumber());
    assertEquals("0002", response.cardLast4());
    verify(bankClient, times(1)).authorize(any());
  }

  @Test
  void sameIdempotencyKeyAndSamePayloadReturnsSamePaymentId() {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResult(true));
    CreatePaymentRequest request = validRequest("4111111111111111");

    PaymentResponse first = service.createPayment("same-key", request);
    PaymentResponse replay = service.createPayment("same-key", request);

    assertEquals(first.paymentId(), replay.paymentId());
    assertEquals(first.status(), replay.status());
    verify(bankClient, times(1)).authorize(any());
  }

  @Test
  void sameIdempotencyKeyAndDifferentPayloadThrowsConflict() {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResult(true));

    service.createPayment("same-key", validRequest("4111111111111111"));

    assertThrows(IdempotencyConflictException.class,
        () -> service.createPayment("same-key", validRequest("4111111111111121")));
    verify(bankClient, times(1)).authorize(any());
  }

  @Test
  void failedIdempotentAttemptDoesNotPoisonKeyAndRetrySucceeds() {
    when(bankClient.authorize(any()))
        .thenThrow(new BankUnavailableException("Bank simulator unavailable"))
        .thenReturn(new BankPaymentResult(true));

    CreatePaymentRequest request = validRequest("4111111111111111");

    assertThrows(BankUnavailableException.class, () -> service.createPayment("retry-key", request));

    PaymentResponse retry = service.createPayment("retry-key", request);

    assertEquals(PaymentStatus.AUTHORIZED, retry.status());
    assertEquals("1111", retry.cardLast4());
    verify(bankClient, times(2)).authorize(any());
  }

  @Test
  void concurrentSameIdempotencyKeyTriggersSingleBankCall() throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(2);
    when(bankClient.authorize(any())).thenAnswer(invocation -> {
      TimeUnit.MILLISECONDS.sleep(200);
      return new BankPaymentResult(true);
    });

    Callable<PaymentResponse> task = () -> {
      barrier.await(2, TimeUnit.SECONDS);
      return service.createPayment("concurrent-key", validRequest("4111111111111111"));
    };

    Future<PaymentResponse> firstFuture = executorService.submit(task);
    Future<PaymentResponse> secondFuture = executorService.submit(task);

    PaymentResponse first = firstFuture.get(5, TimeUnit.SECONDS);
    PaymentResponse second = secondFuture.get(5, TimeUnit.SECONDS);

    assertEquals(first.paymentId(), second.paymentId());
    verify(bankClient, times(1)).authorize(any());
  }

  @Test
  void idempotentReplayReturnsPaymentRecordAfterCardExpiryPasses() {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResult(true));

    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-02-24T12:00:00Z"));
    Clock mutableClock = new Clock() {
      @Override
      public ZoneId getZone() {
        return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(ZoneId zone) {
        return this;
      }

      @Override
      public Instant instant() {
        return now.get();
      }
    };

    InMemoryPaymentsRepository paymentsRepository = new InMemoryPaymentsRepository();
    PaymentExpiryValidator paymentExpiryValidator = new PaymentExpiryValidator(mutableClock);
    PaymentProcessor paymentProcessor = new PaymentProcessor(bankClient, new SimpleMeterRegistry());
    PaymentCreationWorkflow paymentCreationWorkflow =
        new PaymentCreationWorkflow(paymentExpiryValidator, paymentProcessor, paymentsRepository);
    PaymentGatewayService expiringAwareService = new PaymentGatewayService(
        paymentsRepository,
        new DirectPaymentExecutionStrategy(paymentCreationWorkflow),
        new IdempotentPaymentExecutionStrategy(
            new PaymentIdempotencyExecutor(new InMemoryIdempotencyStore(),
                new PaymentRequestFingerprintGenerator()),
            paymentCreationWorkflow));

    CreatePaymentRequest request = new CreatePaymentRequest("4111111111111111", 3, 2026, "GBP", 100,
        "123");

    PaymentResponse first = expiringAwareService.createPayment("stable-replay-key", request);
    now.set(Instant.parse("2026-04-01T00:00:00Z"));
    PaymentResponse replay = expiringAwareService.createPayment("stable-replay-key", request);

    assertEquals(first.paymentId(), replay.paymentId());
    assertEquals(first.status(), replay.status());
    verify(bankClient, times(1)).authorize(any());
  }

  private CreatePaymentRequest validRequest(String cardNumber) {
    return new CreatePaymentRequest(cardNumber, 12, 2026, "GBP", 100, "123");
  }
}
