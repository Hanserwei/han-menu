package com.hanserwei.hanmenu.menu.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable read model owned by the menu bounded context. */
public record MenuEntry(UUID dishId, String name, BigDecimal price) {}
