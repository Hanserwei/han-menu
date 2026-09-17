package com.hanserwei.hanmenu.catalog.domain;

import java.util.Optional;

/** Persistence port owned by the domain, implemented by an infrastructure adapter. */
public interface DishRepository {
  /** Persists a new aggregate. */
  void add(Dish dish);

  /** Loads an aggregate by its identity, if present. */
  Optional<Dish> findById(DishId id);

  /** Updates only if the persisted version still matches the loaded aggregate. */
  void update(Dish dish);
}
