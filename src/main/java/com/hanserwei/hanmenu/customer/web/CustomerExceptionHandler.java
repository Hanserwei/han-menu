package com.hanserwei.hanmenu.customer.web;

import com.hanserwei.hanmenu.customer.domain.CustomerException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 顾客模块异常映射为 RFC 9457，避免泄露 ORM 或存储细节. */
@Order(0)
@RestControllerAdvice
class CustomerExceptionHandler {
  private final Duration loginWindow;

  CustomerExceptionHandler(@Value("${han-menu.customer.login-window}") Duration loginWindow) {
    this.loginWindow = loginWindow;
  }

  @ExceptionHandler(CustomerException.class)
  ProblemDetail business(
      CustomerException exception, HttpServletRequest request, HttpServletResponse response) {
    int status =
        switch (exception.reason()) {
          case INVALID_INPUT -> 400;
          case INVALID_CREDENTIALS -> 401;
          case NOT_FOUND -> 404;
          case CONFLICT, VERSION_CONFLICT -> 409;
          case UNAVAILABLE -> 503;
          case RATE_LIMITED -> 429;
        };
    String code = "CUSTOMER_" + exception.reason().name();
    if (status == 429) {
      response.setHeader("Retry-After", Long.toString(loginWindow.toSeconds()));
    }
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
