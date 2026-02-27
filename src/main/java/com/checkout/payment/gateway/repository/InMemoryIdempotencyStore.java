package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PaymentRecord;
import java.io.Serial;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryIdempotencyStore {

  private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

  public Reservation reserve(String key, String payloadFingerprint) {
    Entry created = new Entry(payloadFingerprint, new CompletableFuture<>());
    Entry existing = entries.putIfAbsent(key, created);

    if (existing == null) {
      return new Reservation(key, created, true);
    }
    if (!Objects.equals(existing.payloadFingerprint, payloadFingerprint)) {
      throw new PayloadConflictException();
    }
    return new Reservation(key, existing, false);
  }

  public void remove(String key, Reservation reservation) {
    entries.remove(key, reservation.entry);
  }

  public static final class Reservation {

    private final String key;
    private final Entry entry;
    private final boolean owner;

    private Reservation(String key, Entry entry, boolean owner) {
      this.key = key;
      this.entry = entry;
      this.owner = owner;
    }

    public String key() {
      return key;
    }

    public CompletableFuture<PaymentRecord> future() {
      return entry.future;
    }

    public boolean owner() {
      return owner;
    }
  }

  private record Entry(String payloadFingerprint, CompletableFuture<PaymentRecord> future) {

  }

  public static final class PayloadConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;
  }
}
