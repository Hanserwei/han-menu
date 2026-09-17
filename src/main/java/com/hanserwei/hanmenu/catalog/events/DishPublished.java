package com.hanserwei.hanmenu.catalog.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A published dish snapshot; consumers must tolerate duplicate deliveries. */
public record DishPublished(
    UUID eventId, UUID dishId, String name, BigDecimal price, Instant occurredAt) {}
