package com.hanserwei.hanmenu.payment.infrastructure;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.internal.util.AlipayLogger;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.hanserwei.hanmenu.payment.domain.PaymentException;
import com.hanserwei.hanmenu.payment.domain.PaymentGateway;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 仅连接固定支付宝沙箱网关，官方 SDK 负责 RSA2 签名和成功响应验签. */
@Component
@Transactional(propagation = Propagation.NEVER)
public class AlipaySandboxGateway implements PaymentGateway {
  private static final String SANDBOX = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
  private static final ZoneId CHANNEL_ZONE = ZoneId.of("Asia/Shanghai");
  private static final DateTimeFormatter CHANNEL_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private final AlipaySettings settings;
  private volatile AlipayClient client;

  /** 禁用 SDK 原始请求和异常日志，应用只记录内部标识及固定故障分类. */
  public AlipaySandboxGateway(AlipaySettings settings) {
    this.settings = settings;
    AlipayLogger.setNeedEnableLogger(false);
    AlipayLogger.setJDKDebugEnabled(false);
  }

  @Override
  public void requireConfigured() {
    if (!SANDBOX.equals(settings.gateway())
        || settings.appId() == null
        || !settings.appId().matches("[0-9]{16}")
        || settings.sellerId() == null
        || !settings.sellerId().matches("[0-9]{16}")
        || settings.privateKey() == null
        || settings.privateKey().isBlank()
        || settings.publicKey() == null
        || settings.publicKey().isBlank()) {
      throw unavailable();
    }
  }

  private AlipayClient client() {
    requireConfigured();
    var value = client;
    if (value == null) {
      synchronized (this) {
        if (client == null) {
          var created =
              new DefaultAlipayClient(
                  SANDBOX,
                  settings.appId(),
                  settings.privateKey(),
                  "json",
                  "UTF-8",
                  settings.publicKey(),
                  "RSA2");
          created.setConnectTimeout(3000);
          created.setReadTimeout(8000);
          client = created;
        }
        value = client;
      }
    }
    return value;
  }

  @Override
  public String appParameters(TradeRequest trade) {
    requireConfigured();
    try {
      var notify = URI.create(settings.notifyUrl());
      if (!"https".equals(notify.getScheme())
          || notify.getHost() == null
          || notify.getUserInfo() != null) {
        throw unavailable();
      }
      var model = new AlipayTradeAppPayModel();
      model.setOutTradeNo(trade.paymentId().toString());
      model.setTotalAmount(trade.amount().toPlainString());
      model.setSubject("Han Menu 外卖订单");
      model.setProductCode("QUICK_MSECURITY_PAY");
      model.setSellerId(settings.sellerId());
      model.setTimeExpire(CHANNEL_TIME.format(trade.expiresAt().atZone(CHANNEL_ZONE)));
      var request = new AlipayTradeAppPayRequest();
      request.setBizModel(model);
      request.setNotifyUrl(notify.toASCIIString());
      return client().sdkExecute(request).getBody();
    } catch (AlipayApiException | IllegalArgumentException | NullPointerException exception) {
      throw unavailable();
    }
  }

  @Override
  public TradeResult query(TradeRequest trade) {
    try {
      var model = new AlipayTradeQueryModel();
      model.setOutTradeNo(trade.paymentId().toString());
      var request = new AlipayTradeQueryRequest();
      request.setBizModel(model);
      var response = client().execute(request);
      if (!response.isSuccess()) {
        if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
          return new TradeResult(State.NOT_FOUND, null, null, null);
        }
        throw unavailable();
      }
      requireEqual(trade.paymentId().toString(), response.getOutTradeNo());
      var amount = amount(response.getTotalAmount());
      if (amount.compareTo(trade.amount()) != 0) {
        throw invalid();
      }
      var state = state(response.getTradeStatus());
      var paidAt = response.getSendPayDate() == null ? null : response.getSendPayDate().toInstant();
      if (state == State.SUCCEEDED && paidAt == null) {
        throw invalid();
      }
      return new TradeResult(state, response.getTradeNo(), amount, paidAt);
    } catch (AlipayApiException exception) {
      throw unavailable();
    }
  }

  @Override
  public TradeResult close(TradeRequest trade) {
    try {
      var model = new AlipayTradeCloseModel();
      model.setOutTradeNo(trade.paymentId().toString());
      var request = new AlipayTradeCloseRequest();
      request.setBizModel(model);
      var response = client().execute(request);
      // 即使关单成功也再查一次，已付款竞争和丢失响应由真实渠道事实决定。
      if (response.isSuccess()) {
        requireEqual(trade.paymentId().toString(), response.getOutTradeNo());
      }
      return query(trade);
    } catch (AlipayApiException exception) {
      throw unavailable();
    }
  }

  @Override
  public void refund(RefundRequest refund) {
    try {
      var model = new AlipayTradeRefundModel();
      model.setOutTradeNo(refund.paymentId().toString());
      model.setTradeNo(refund.tradeNo());
      model.setOutRequestNo(refund.refundId().toString());
      model.setRefundAmount(refund.amount().toPlainString());
      model.setRefundReason("订单取消退款");
      var request = new AlipayTradeRefundRequest();
      request.setBizModel(model);
      var response = client().execute(request);
      if (!response.isSuccess()) {
        throw unavailable();
      }
      requireEqual(refund.paymentId().toString(), response.getOutTradeNo());
      requireEqual(refund.tradeNo(), response.getTradeNo());
      if (amount(response.getRefundFee()).compareTo(refund.amount()) != 0) {
        throw invalid();
      }
    } catch (AlipayApiException exception) {
      throw unavailable();
    }
  }

  @Override
  public boolean refundSucceeded(RefundRequest refund) {
    try {
      var model = new AlipayTradeFastpayRefundQueryModel();
      model.setOutTradeNo(refund.paymentId().toString());
      model.setTradeNo(refund.tradeNo());
      model.setOutRequestNo(refund.refundId().toString());
      var request = new AlipayTradeFastpayRefundQueryRequest();
      request.setBizModel(model);
      var response = client().execute(request);
      if (!response.isSuccess()) {
        if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
          return false;
        }
        throw unavailable();
      }
      // 官方退款查询在尚无退款记录时也可能返回 10000 且没有业务字段。
      // 只有明确的退款成功才校验交易引用和金额，空记录必须允许发起同一退款意图。
      if (!"REFUND_SUCCESS".equals(response.getRefundStatus())) {
        return false;
      }
      requireEqual(refund.paymentId().toString(), response.getOutTradeNo());
      requireEqual(refund.tradeNo(), response.getTradeNo());
      requireEqual(refund.refundId().toString(), response.getOutRequestNo());
      if (amount(response.getRefundAmount()).compareTo(refund.amount()) != 0
          || amount(response.getTotalAmount()).compareTo(refund.amount()) != 0) {
        throw invalid();
      }
      return true;
    } catch (AlipayApiException exception) {
      throw unavailable();
    }
  }

  @Override
  public Notice verify(Map<String, String> input) {
    requireConfigured();
    try {
      if (input.size() > 64
          || input.entrySet().stream()
              .anyMatch(entry -> entry.getValue() == null || entry.getValue().length() > 4096)) {
        throw invalid();
      }
      requireEqual("RSA2", input.get("sign_type"));
      requireEqual(settings.appId(), input.get("app_id"));
      requireEqual(settings.sellerId(), input.get("seller_id"));
      if (!AlipaySignature.rsaCheckV1(
          new HashMap<>(input), settings.publicKey(), "UTF-8", "RSA2")) {
        throw invalid();
      }
      var state = state(input.get("trade_status"));
      var paidAt =
          state == State.SUCCEEDED
              ? LocalDateTime.parse(input.get("gmt_payment"), CHANNEL_TIME)
                  .atZone(CHANNEL_ZONE)
                  .toInstant()
              : null;
      var tradeNo = input.get("trade_no");
      if (tradeNo == null || !tradeNo.matches("[0-9]{16,64}")) {
        throw invalid();
      }
      return new Notice(
          UUID.fromString(input.get("out_trade_no")),
          new TradeResult(state, tradeNo, amount(input.get("total_amount")), paidAt));
    } catch (AlipayApiException
        | IllegalArgumentException
        | java.time.DateTimeException
        | NullPointerException exception) {
      throw invalid();
    }
  }

  private State state(String status) {
    return switch (status == null ? "" : status) {
      case "WAIT_BUYER_PAY" -> State.PENDING;
      case "TRADE_SUCCESS", "TRADE_FINISHED" -> State.SUCCEEDED;
      case "TRADE_CLOSED" -> State.CLOSED;
      default -> throw invalid();
    };
  }

  private BigDecimal amount(String text) {
    try {
      var value = new BigDecimal(text).setScale(2, java.math.RoundingMode.UNNECESSARY);
      if (value.signum() <= 0) {
        throw invalid();
      }
      return value;
    } catch (NumberFormatException | ArithmeticException | NullPointerException exception) {
      throw invalid();
    }
  }

  private void requireEqual(String expected, String actual) {
    if (!expected.equals(actual)) {
      throw invalid();
    }
  }

  private PaymentException unavailable() {
    return new PaymentException(PaymentException.Reason.UNAVAILABLE, "支付宝沙箱暂不可用或尚未配置");
  }

  private PaymentException invalid() {
    return new PaymentException(PaymentException.Reason.INVALID_NOTIFICATION, "支付宝交易事实校验失败");
  }
}
