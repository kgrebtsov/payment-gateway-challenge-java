package com.checkout.payment.gateway.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bank.simulator")
public record BankClientProperties(
    String baseUrl,
    String paymentsPath
) {

}
