package com.hanserwei.hanmenu.catalog.application;

import com.hanserwei.hanmenu.catalog.domain.Dish;
import com.hanserwei.hanmenu.catalog.domain.DishId;
import com.hanserwei.hanmenu.catalog.domain.DishRepository;
import com.hanserwei.hanmenu.catalog.domain.Price;
import com.hanserwei.hanmenu.catalog.events.DishPublished;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates use cases and commits aggregate state and event publication atomically. */
@Service
@Transactional
public class CatalogService {
  private final DishRepository dishes;
  private final ApplicationEventPublisher events;

  /** Connects the domain persistence port and transactional event publisher. */
  public CatalogService(DishRepository dishes, ApplicationEventPublisher events) {
    this.dishes = dishes;
    this.events = events;
  }

  /** Creates a draft and returns its assigned identity. */
  public UUID create(String name, BigDecimal price) {
    var dish = Dish.draft(new DishId(UUID.randomUUID()), name, new Price(price));
    dishes.add(dish);
    return dish.id().value();
  }

  /** Publishes a draft and records its integration event in the same transaction. */
  public void publish(UUID id) {
    var dish = dishes.findById(new DishId(id)).orElseThrow(() -> new DishNotFoundException(id));
    dish.publish();
    dishes.update(dish);
    events.publishEvent(
        new DishPublished(
            UUID.randomUUID(), id, dish.name(), dish.price().amount(), Instant.now()));
  }
}
