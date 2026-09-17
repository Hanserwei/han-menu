package com.hanserwei.hanmenu.menu.application;

import com.hanserwei.hanmenu.catalog.events.DishPublished;
import com.hanserwei.hanmenu.menu.domain.MenuEntry;
import com.hanserwei.hanmenu.menu.domain.MenuRepository;
import java.util.List;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the menu asynchronously in its own transaction, then serves local reads. */
@Service
public class MenuService {
  private final MenuRepository menu;

  /** Connects the menu persistence port. */
  public MenuService(MenuRepository menu) {
    this.menu = menu;
  }

  /** Applies a publication to the menu in an independent, idempotent transaction. */
  @ApplicationModuleListener
  public void on(DishPublished event) {
    menu.addIfAbsent(new MenuEntry(event.dishId(), event.name(), event.price()));
  }

  /** Reads the menu without calling back into the catalog. */
  @Transactional(readOnly = true)
  public List<MenuEntry> findAll() {
    return menu.findAll();
  }
}
