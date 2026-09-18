package com.hanserwei.hanmenu.cart.web;

import com.hanserwei.hanmenu.cart.domain.CartException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 购物车模块异常映射为 RFC 9457，避免泄露 ORM 或存储细节. */
@Order(0)
@RestControllerAdvice
class CartExceptionHandler {
  @ExceptionHandler(CartException.class)
  ProblemDetail business(CartException exception, HttpServletRequest request) {
    int status =
        switch (exception.reason()) {
          case INVALID_INPUT -> 400;
          case NOT_FOUND -> 404;
          case CONFLICT, VERSION_CONFLICT -> 409;
          case UNAVAILABLE -> 503;
        };
    String code = "CART_" + exception.reason().name();
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
