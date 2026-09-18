package com.hanserwei.hanmenu.shop.web;

import com.hanserwei.hanmenu.shop.domain.ShopException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 模块业务异常先于通用兜底处理，保持 RFC 9457 协议和追踪标识. */
@Order(0)
@RestControllerAdvice
class ShopExceptionHandler {
  @ExceptionHandler(ShopException.class)
  ProblemDetail business(ShopException exception, HttpServletRequest request) {
    int status =
        switch (exception.reason()) {
          case INVALID_INPUT -> 400;
          case CONFLICT, VERSION_CONFLICT -> 409;
        };
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), exception.getMessage());
    String code = "SHOP_" + exception.reason().name();
    problem.setType(
        URI.create("urn:han-menu:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    problem.setProperty("traceId", MDC.get("requestId"));
    return problem;
  }
}
