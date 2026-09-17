package com.hanserwei.hanmenu.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** Identifies a catalog aggregate without exposing a persistence entity. */
public record DishId(UUID value) {
  /** Rejects missing identifiers. */
  public DishId {
    Objects.requireNonNull(value, "Dish ID is required");
  }
}
