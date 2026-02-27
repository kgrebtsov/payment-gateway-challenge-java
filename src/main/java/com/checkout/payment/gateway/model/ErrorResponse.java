package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.ErrorStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(ErrorStatus status, List<ErrorDetail> errors) {

  public static ErrorResponse single(String field, String message) {
    return new ErrorResponse(null, List.of(new ErrorDetail(field, message)));
  }

  public static ErrorResponse rejected(String field, String message) {
    return new ErrorResponse(ErrorStatus.REJECTED, List.of(new ErrorDetail(field, message)));
  }

  public static ErrorResponse rejected(List<ErrorDetail> errors) {
    return new ErrorResponse(ErrorStatus.REJECTED, errors);
  }
}
