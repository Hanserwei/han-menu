package com.hanserwei.hanmenu.catalog.infrastructure;

import com.hanserwei.hanmenu.catalog.domain.Dish;
import com.hanserwei.hanmenu.catalog.domain.DishId;
import com.hanserwei.hanmenu.catalog.domain.DishRepository;
import com.hanserwei.hanmenu.catalog.domain.Price;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcDishRepository implements DishRepository {
  private final JdbcClient jdbc;

  JdbcDishRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void add(Dish dish) {
    jdbc.sql("INSERT INTO catalog_dish (id, name, price, status, version) VALUES (?, ?, ?, ?, ?)")
        .params(dish.id().value(), dish.name(), dish.price().amount(), dish.status().name(), 0)
        .update();
  }

  @Override
  public Optional<Dish> findById(DishId id) {
    return jdbc.sql("SELECT id, name, price, status, version FROM catalog_dish WHERE id = ?")
        .param(id.value())
        .query(
            (row, index) ->
                Dish.restore(
                    new DishId(row.getObject("id", UUID.class)),
                    row.getString("name"),
                    new Price(row.getBigDecimal("price")),
                    Dish.Status.valueOf(row.getString("status")),
                    row.getLong("version")))
        .optional();
  }

  @Override
  public void update(Dish dish) {
    int updated =
        jdbc.sql(
                "UPDATE catalog_dish SET status = ?, version = version + 1"
                    + " WHERE id = ? AND version = ?")
            .params(dish.status().name(), dish.id().value(), dish.version())
            .update();
    if (updated != 1) {
      throw new OptimisticLockingFailureException("Dish was changed by another request");
    }
  }
}
