package com.checkout.payment.gateway.configuration;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(BankClientProperties.class)
public class ApplicationConfiguration {

  @Bean
  public RestTemplate restTemplate(
      RestTemplateBuilder builder,
      @Value("${bank.simulator.connect-timeout-ms:2000}") long connectTimeoutMs,
      @Value("${bank.simulator.read-timeout-ms:2000}") long readTimeoutMs) {
    return builder
        .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
        .setReadTimeout(Duration.ofMillis(readTimeoutMs))
        .build();
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
