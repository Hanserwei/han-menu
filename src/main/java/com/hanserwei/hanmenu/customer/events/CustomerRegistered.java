package com.hanserwei.hanmenu.customer.events;

import java.time.Instant;
import java.util.UUID;

/** 顾客注册统计事实，只包含内部标识与创建时间，不包含手机号或姓名. */
public record CustomerRegistered(UUID customerId, Instant createdAt) {}
