package com.checkout.payment.gateway.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PaymentRecord;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryIdempotencyStoreTest {

  private InMemoryIdempotencyStore store;

  @BeforeEach
  void setUp() {
    store = new InMemoryIdempotencyStore();
  }

  @Test
  void firstReservationOwnsAndSecondWithSamePayloadReusesFuture() {
    InMemoryIdempotencyStore.Reservation first = store.reserve("k1", "fp1");
    InMemoryIdempotencyStore.Reservation second = store.reserve("k1", "fp1");

    assertTrue(first.owner());
    assertFalse(second.owner());
    assertSame(first.future(), second.future());
  }

  @Test
  void sameKeyWithDifferentPayloadThrowsConflict() {
    store.reserve("k1", "fp1");

    assertThrows(InMemoryIdempotencyStore.PayloadConflictException.class,
        () -> store.reserve("k1", "fp2"));
  }

  @Test
  void removeAllowsFreshReservationForSameKey() {
    InMemoryIdempotencyStore.Reservation first = store.reserve("k1", "fp1");

    store.remove("k1", first);

    InMemoryIdempotencyStore.Reservation next = store.reserve("k1", "fp2");
    assertTrue(next.owner());
  }

  @Test
  void sharedFutureCarriesStoredResultToReplays() {
    InMemoryIdempotencyStore.Reservation owner = store.reserve("k1", "fp1");
    InMemoryIdempotencyStore.Reservation replay = store.reserve("k1", "fp1");

    PaymentRecord payment = new PaymentRecord(
        UUID.randomUUID(), PaymentStatus.AUTHORIZED, 100, "USD", "4242", 1, 2027);
    owner.future().complete(payment);

    assertEquals(payment, replay.future().join());
  }
}
