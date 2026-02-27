package com.checkout.payment.gateway.service.idempotency;

import com.checkout.payment.gateway.model.CreatePaymentRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestFingerprintGenerator {

  public String generate(CreatePaymentRequest request) {
    String raw = String.join("|",
        request.cardNumber(),
        request.expiryMonth().toString(),
        request.expiryYear().toString(),
        request.currency().toUpperCase(Locale.ROOT),
        request.amount().toString(),
        request.cvv());
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }
}
