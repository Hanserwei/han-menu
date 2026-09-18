package com.hanserwei.hanmenu.catalog.web;

import com.hanserwei.hanmenu.catalog.domain.CatalogException;
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
class CatalogExceptionHandler {
  @ExceptionHandler(CatalogException.class)
  ProblemDetail business(CatalogException exception, HttpServletRequest request) {
    int status =
        switch (exception.reason()) {
          case INVALID_INPUT -> 400;
          case NOT_FOUND -> 404;
          case CONFLICT, VERSION_CONFLICT -> 409;
          case UNAVAILABLE -> 503;
        };
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), exception.getMessage());
    String code = "CATALOG_" + exception.reason().name();
    problem.setType(
        URI.create("urn:han-menu:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    problem.setProperty("traceId", MDC.get("requestId"));
    return problem;
  }
}
