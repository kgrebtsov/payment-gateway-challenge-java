package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PaymentRecord;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPaymentsRepository {

  private final Map<UUID, PaymentRecord> payments = new ConcurrentHashMap<>();

  public void save(PaymentRecord payment) {
    payments.put(payment.paymentId(), payment);
  }

  public Optional<PaymentRecord> findById(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }
}
