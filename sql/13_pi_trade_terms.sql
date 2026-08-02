-- =====================================================================
-- PI 外贸要素字段 (付款方式/贸易条款/交货期/目的港/唛头)
-- 加载: mysql -u root -proot --default-character-set=utf8mb4 -e "source sql/13_pi_trade_terms.sql"
-- 若列已存在会报 Duplicate column, 可安全忽略
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

ALTER TABLE `sales_pi` ADD COLUMN `payment_method` VARCHAR(128) DEFAULT NULL COMMENT '付款方式' AFTER `sign_date`;
ALTER TABLE `sales_pi` ADD COLUMN `trade_term`     VARCHAR(128) DEFAULT NULL COMMENT '贸易条款Incoterms' AFTER `payment_method`;
ALTER TABLE `sales_pi` ADD COLUMN `delivery_date`  DATE         DEFAULT NULL COMMENT '交货期' AFTER `trade_term`;
ALTER TABLE `sales_pi` ADD COLUMN `dest_port`      VARCHAR(128) DEFAULT NULL COMMENT '目的港' AFTER `delivery_date`;
ALTER TABLE `sales_pi` ADD COLUMN `shipping_marks` VARCHAR(512) DEFAULT NULL COMMENT '唛头' AFTER `dest_port`;
