package com.hanserwei.hanmenu.ordering.web;

import com.hanserwei.hanmenu.ordering.domain.OrderException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 订单模块异常映射为 RFC 9457，避免泄露 ORM 或存储细节. */
@Order(0)
@RestControllerAdvice
class OrderExceptionHandler {
  @ExceptionHandler(OrderException.class)
  ProblemDetail business(OrderException exception, HttpServletRequest request) {
    int status =
        switch (exception.reason()) {
          case INVALID_INPUT -> 400;
          case NOT_FOUND -> 404;
          case SHOP_CLOSED, VERSION_CONFLICT, STATE_CONFLICT, IDEMPOTENCY_CONFLICT -> 409;
        };
    String code = "ORDER_" + exception.reason().name();
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), exception.getMessage());
    problem.setType(
        URI.create("urn:han-menu:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    problem.setProperty("traceId", MDC.get("requestId"));
    return problem;
  }
}
