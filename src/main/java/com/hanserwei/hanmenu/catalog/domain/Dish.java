package com.hanserwei.hanmenu.catalog.domain;

import java.util.Objects;

/** Aggregate root enforcing the draft-to-published lifecycle. */
public final class Dish {
  private final DishId id;
  private final String name;
  private final Price price;
  private Status status;
  private final long version;

  private Dish(DishId id, String name, Price price, Status status, long version) {
    this.id = Objects.requireNonNull(id, "Dish ID is required");
    this.name = Objects.requireNonNull(name, "Name is required").strip();
    if (this.name.isBlank() || this.name.length() > 100) {
      throw new IllegalArgumentException("Dish name must contain 1 to 100 characters");
    }
    this.price = Objects.requireNonNull(price, "Price is required");
    this.status = Objects.requireNonNull(status, "Status is required");
    if (version < 0) {
      throw new IllegalArgumentException("Version must not be negative");
    }
    this.version = version;
  }

  /** Creates a new aggregate in the draft state. */
  public static Dish draft(DishId id, String name, Price price) {
    return new Dish(id, name, price, Status.DRAFT, 0);
  }

  /** Reconstitutes an aggregate from a previously persisted snapshot. */
  public static Dish restore(DishId id, String name, Price price, Status status, long version) {
    return new Dish(id, name, price, status, version);
  }

  /** Publishes exactly once; publishing an already published aggregate is a conflict. */
  public void publish() {
    status =
        switch (status) {
          case DRAFT -> Status.PUBLISHED;
          case PUBLISHED -> throw new IllegalStateException("Dish is already published");
        };
  }

  /** Returns the stable aggregate identity. */
  public DishId id() {
    return id;
  }

  /** Returns the normalized display name. */
  public String name() {
    return name;
  }

  /** Returns the exact price in CNY. */
  public Price price() {
    return price;
  }

  /** Returns the current publication state. */
  public Status status() {
    return status;
  }

  /** Returns the loaded persistence version used for optimistic concurrency checks. */
  public long version() {
    return version;
  }

  /** Allowed lifecycle states. */
  public enum Status {
    DRAFT,
    PUBLISHED
  }
}
