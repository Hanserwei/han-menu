package com.hanserwei.hanmenu.catalog.application;

import java.util.UUID;

/** Indicates that the requested aggregate does not exist. */
public final class DishNotFoundException extends RuntimeException {
  /** Records the identity that could not be found. */
  public DishNotFoundException(UUID id) {
    super("Dish not found: " + id);
  }
}
