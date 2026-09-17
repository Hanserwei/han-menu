package com.hanserwei.hanmenu.identity.web;

import com.hanserwei.hanmenu.identity.domain.IdentityException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 映射身份用例失败，禁止把校验异常的 rejectedValue 或底层 SQL 直接写入响应. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class IdentityExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(IdentityExceptionHandler.class);
  private final SecurityResponses responses;
  private final Duration loginWindow;

  IdentityExceptionHandler(
      SecurityResponses responses,
      @Value("${han-menu.identity.login-window}") Duration loginWindow) {
    this.responses = responses;
    this.loginWindow = loginWindow;
  }

  @ExceptionHandler(IdentityException.class)
  void business(
      IdentityException exception, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    int status =
        switch (exception.reason()) {
          case INVALID_CREDENTIALS -> 401;
          case FORBIDDEN -> 403;
          case NOT_FOUND -> 404;
          case CONFLICT -> 409;
          case INVALID_INPUT -> 400;
          case RATE_LIMITED -> 429;
          case UNAVAILABLE -> 503;
        };
    if (status == 429) {
      response.setHeader("Retry-After", Long.toString(loginWindow.toSeconds()));
    }
    responses.write(request, response, status, exception.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  void invalid(Exception exception, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    responses.write(request, response, 400, "请求参数不合法");
  }

  @ExceptionHandler(DuplicateKeyException.class)
  void duplicate(HttpServletRequest request, HttpServletResponse response) throws IOException {
    responses.write(request, response, 409, "用户名已存在");
  }

  @ExceptionHandler(DataAccessException.class)
  void unavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
    responses.write(request, response, 503, "服务暂不可用，请稍后重试");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  void method(HttpServletRequest request, HttpServletResponse response) throws IOException {
    responses.write(request, response, 405, "请求方法不受支持");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  void mediaType(HttpServletRequest request, HttpServletResponse response) throws IOException {
    responses.write(request, response, 415, "请使用 application/json 请求内容");
  }

  @ExceptionHandler(Exception.class)
  void unexpected(Exception exception, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    if (exception instanceof ErrorResponse error) {
      responses.write(request, response, error.getStatusCode().value(), "请求无法处理");
      return;
    }
    // 异常消息可能包含 SQL 参数或请求值；诊断只输出异常类型，由追踪标识关联请求。
    LOGGER.error("request_failure exceptionType={}", exception.getClass().getSimpleName());
    responses.write(request, response, 500, "服务内部错误");
  }
}
