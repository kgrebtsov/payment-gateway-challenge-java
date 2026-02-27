package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @PostMapping("/payments")
  public ResponseEntity<PaymentResponse> createPayment(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreatePaymentRequest request) {
    return ResponseEntity.ok(paymentGatewayService.createPayment(idempotencyKey, request));
  }

  @GetMapping("/payments/{paymentId}")
  public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
    return ResponseEntity.ok(paymentGatewayService.getPaymentById(paymentId));
  }
}
