package com.hanserwei.hanmenu.menu.infrastructure;

import com.hanserwei.hanmenu.menu.domain.MenuEntry;
import com.hanserwei.hanmenu.menu.domain.MenuRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMenuRepository implements MenuRepository {
  private final JdbcClient jdbc;

  JdbcMenuRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void addIfAbsent(MenuEntry entry) {
    jdbc.sql(
            "INSERT INTO menu_entry (dish_id, name, price) VALUES (?, ?, ?)"
                + " ON CONFLICT (dish_id) DO NOTHING")
        .params(entry.dishId(), entry.name(), entry.price())
        .update();
  }

  @Override
  public List<MenuEntry> findAll() {
    return jdbc.sql("SELECT dish_id, name, price FROM menu_entry ORDER BY name, dish_id")
        .query(
            (row, index) ->
                new MenuEntry(
                    row.getObject("dish_id", UUID.class),
                    row.getString("name"),
                    row.getBigDecimal("price")))
        .list();
  }
}
