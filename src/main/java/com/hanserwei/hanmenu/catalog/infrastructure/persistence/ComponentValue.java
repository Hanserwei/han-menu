package com.hanserwei.hanmenu.catalog.infrastructure.persistence;

import com.hanserwei.hanmenu.catalog.domain.MealComponent;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 套餐明细作为聚合内值对象存储，菜品引用受模块内外键保护. */
@Embeddable
public class ComponentValue {
  @Column(nullable = false)
  UUID dishId;

  @Column(nullable = false)
  int quantity;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  Map<String, String> selections;

  /** ORM 构造入口. */
  protected ComponentValue() {}

  static ComponentValue from(MealComponent value) {
    var result = new ComponentValue();
    result.dishId = value.dishId();
    result.quantity = value.quantity();
    result.selections = value.selections();
    return result;
  }

  MealComponent domain() {
    return new MealComponent(dishId, quantity, selections);
  }
}
