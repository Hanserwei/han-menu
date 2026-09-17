-- V1、V2 已在开发库执行，保留原文件以维护 Flyway 校验和与迁移历史。
-- 重命名旧演示表以保留既有数据；正式业务模型在后续版本中独立建立。
ALTER TABLE catalog_dish RENAME TO legacy_demo_catalog_dish;
ALTER TABLE menu_entry RENAME TO legacy_demo_menu_entry;

-- 同步释放原主键索引名称，避免后续正式业务表使用相同名称时产生冲突。
ALTER TABLE legacy_demo_catalog_dish
    RENAME CONSTRAINT catalog_dish_pkey TO legacy_demo_catalog_dish_pkey;
ALTER TABLE legacy_demo_menu_entry
    RENAME CONSTRAINT menu_entry_pkey TO legacy_demo_menu_entry_pkey;
