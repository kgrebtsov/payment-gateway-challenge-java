package com.checkout.payment.gateway.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
    @Schema(description = "Primary account number (PAN). Digits only, 14-19 chars.", example = "4111111111111111")
    @NotBlank(message = "cardNumber is required")
    @Pattern(regexp = "\\d+", message = "cardNumber must contain digits only")
    @Size(min = 14, max = 19, message = "cardNumber length must be between 14 and 19")
    String cardNumber,

    @Schema(description = "Card expiry month (1-12).", example = "12")
    @NotNull(message = "expiryMonth is required")
    @Min(value = 1, message = "expiryMonth must be between 1 and 12")
    @Max(value = 12, message = "expiryMonth must be between 1 and 12")
    Integer expiryMonth,

    @Schema(description = "Card expiry year. Combined with expiryMonth must be in the future.", example = "2027")
    @NotNull(message = "expiryYear is required")
    @Min(value = 1000, message = "expiryYear must be a 4-digit year")
    @Max(value = 9999, message = "expiryYear must be a 4-digit year")
    Integer expiryYear,

    @Schema(description = "ISO 4217 currency code. Supported values: GBP, EUR, USD.", example = "USD")
    @NotBlank(message = "currency is required")
    @Pattern(regexp = "GBP|EUR|USD", message = "currency must be one of GBP, EUR, USD")
    String currency,

    @Schema(
        description = "Amount in minor units (integer). For USD/EUR/GBP, the last two digits are cents/pence. Example: $10.50 -> 1050.",
        example = "1050")
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    Integer amount,

    @Schema(description = "Card verification value. Digits only, 3-4 chars.", example = "123")
    @NotBlank(message = "cvv is required")
    @Pattern(regexp = "\\d+", message = "cvv must contain digits only")
    @Size(min = 3, max = 4, message = "cvv length must be 3 or 4")
    String cvv
) {

}
