package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.BankClientException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@Component
public class BankClientErrorTranslator {

  private static final Logger LOG = LoggerFactory.getLogger(BankClientErrorTranslator.class);

  public RuntimeException translate(RestClientException ex) {
    if (ex instanceof HttpStatusCodeException httpStatusCodeException) {
      return translateHttpStatusException(httpStatusCodeException);
    }
    if (ex instanceof ResourceAccessException resourceAccessException) {
      return translateTransportError(resourceAccessException);
    }
    return translateClientError(ex);
  }

  public RuntimeException translateHttpStatusException(HttpStatusCodeException ex) {
    int statusCode = ex.getStatusCode().value();
    if (isTransientBankStatus(statusCode)) {
      LOG.warn("bank.call.unavailable",
          keyValue("event", "bank.call.unavailable"),
          keyValue("httpStatus", statusCode),
          keyValue("exception", ex.getClass().getSimpleName()),
          keyValue("errorMessage", ex.getMessage()),
          ex);
      return new BankUnavailableException("Bank simulator unavailable", ex);
    }
    LOG.warn("bank.call.error",
        keyValue("event", "bank.call.error"),
        keyValue("httpStatus", statusCode),
        keyValue("exception", ex.getClass().getSimpleName()),
        keyValue("errorMessage", ex.getMessage()),
        ex);
    return new BankClientException("Bank simulator error", ex);
  }

  public RuntimeException translateTransportError(ResourceAccessException ex) {
    LOG.warn("bank.call.transport_error",
        keyValue("event", "bank.call.transport_error"),
        keyValue("exception", ex.getClass().getSimpleName()),
        keyValue("errorMessage", ex.getMessage()),
        ex);
    return new BankUnavailableException("Bank simulator unavailable", ex);
  }

  public RuntimeException translateClientError(RestClientException ex) {
    LOG.warn("bank.call.client_error",
        keyValue("event", "bank.call.client_error"),
        keyValue("exception", ex.getClass().getSimpleName()),
        keyValue("errorMessage", ex.getMessage()),
        ex);
    return new BankClientException("Bank simulator error", ex);
  }

  private boolean isTransientBankStatus(int statusCode) {
    return switch (statusCode) {
      case 502, 503, 504 -> true;
      default -> false;
    };
  }
}
