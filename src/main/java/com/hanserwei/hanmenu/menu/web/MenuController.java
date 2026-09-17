package com.hanserwei.hanmenu.menu.web;

import com.hanserwei.hanmenu.menu.application.MenuService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu")
class MenuController {
  private final MenuService menu;

  MenuController(MenuService menu) {
    this.menu = menu;
  }

  @GetMapping
  List<MenuItem> findAll() {
    return menu.findAll().stream()
        .map(entry -> new MenuItem(entry.dishId(), entry.name(), entry.price()))
        .toList();
  }

  record MenuItem(UUID dishId, String name, BigDecimal price) {}
}
