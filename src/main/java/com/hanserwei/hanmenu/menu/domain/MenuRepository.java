package com.hanserwei.hanmenu.menu.domain;

import java.util.List;

/** Port for the independently stored published menu. */
public interface MenuRepository {
  /** Adds a published dish, ignoring repeated deliveries of the same publication. */
  void addIfAbsent(MenuEntry entry);

  /** Returns all published entries in a stable order. */
  List<MenuEntry> findAll();
}
