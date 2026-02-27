package com.checkout.payment.gateway.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BankPaymentRequest(
    @JsonProperty("card_number") String cardNumber,
    @JsonProperty("expiry_date") String expiryDate,
    String currency,
    Integer amount,
    String cvv
) {

}
