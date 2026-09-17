package com.hanserwei.hanmenu.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** A positive CNY price, represented exactly to two decimal places. */
public record Price(BigDecimal amount) {
  /** Validates the monetary range and rejects lossy rounding. */
  public Price {
    Objects.requireNonNull(amount, "Price is required");
    if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
      throw new IllegalArgumentException("Price must be between 0.01 and 99999999.99 CNY");
    }
    try {
      amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Price must have at most two decimal places", exception);
    }
  }
}
