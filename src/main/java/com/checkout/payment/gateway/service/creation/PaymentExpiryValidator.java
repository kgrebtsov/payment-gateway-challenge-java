package com.checkout.payment.gateway.service.creation;

import com.checkout.payment.gateway.exception.InvalidPaymentRequestException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

@Component
public class PaymentExpiryValidator {

  private final Clock clock;

  public PaymentExpiryValidator(Clock clock) {
    this.clock = clock;
  }

  public void validateFutureExpiry(Integer expiryMonth, Integer expiryYear) {
    if (expiryMonth == null || expiryYear == null) {
      return;
    }

    YearMonth expiry = YearMonth.of(expiryYear, expiryMonth);
    YearMonth current = YearMonth.from(LocalDate.now(clock));
    if (!expiry.isAfter(current)) {
      throw new InvalidPaymentRequestException("expiryMonth", "Expiry date must be in the future");
    }
  }
}
