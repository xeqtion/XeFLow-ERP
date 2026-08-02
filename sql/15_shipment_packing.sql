-- =====================================================================
-- 出货单装箱/物流字段 (箱数/毛净重/CBM/柜型/柜号/提单号)
-- 加载: mysql -u root -proot --default-character-set=utf8mb4 -e "source sql/15_shipment_packing.sql"
-- 若列已存在会报 Duplicate column, 可安全忽略
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

ALTER TABLE `shipment` ADD COLUMN `package_count`  INT           DEFAULT NULL COMMENT '箱数/件数' AFTER `qty`;
ALTER TABLE `shipment` ADD COLUMN `gross_weight`   DECIMAL(18,3) DEFAULT NULL COMMENT '毛重kg' AFTER `package_count`;
ALTER TABLE `shipment` ADD COLUMN `net_weight`     DECIMAL(18,3) DEFAULT NULL COMMENT '净重kg' AFTER `gross_weight`;
ALTER TABLE `shipment` ADD COLUMN `volume_cbm`     DECIMAL(18,3) DEFAULT NULL COMMENT '体积CBM' AFTER `net_weight`;
ALTER TABLE `shipment` ADD COLUMN `container_type` VARCHAR(32)   DEFAULT NULL COMMENT '柜型' AFTER `volume_cbm`;
ALTER TABLE `shipment` ADD COLUMN `container_no`   VARCHAR(64)   DEFAULT NULL COMMENT '柜号' AFTER `container_type`;
ALTER TABLE `shipment` ADD COLUMN `bl_no`          VARCHAR(64)   DEFAULT NULL COMMENT '提单号' AFTER `container_no`;
