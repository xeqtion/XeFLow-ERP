-- =====================================================================
-- 演示业务数据 —— 试运营版 (trial-v1.0) 已停用
-- ---------------------------------------------------------------------
-- 试运营要求系统内不预置任何演示业务数据 (客户/产品/PI/PO/出货/收付款等),
-- 故本文件仅保留"清理"逻辑: 删除历史可能残留的演示数据 (id 90000~99999),
-- 不再写入任何演示记录。需要演示数据时可从 git 历史恢复本文件的 INSERT 段。
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

-- 清理可能残留的演示数据 (固定 id 段 90000~99999), 保证试运营库干净且可重复执行
DELETE FROM `finance_entry`       WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `shipment`            WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `shipping_instruction`WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `purchase_order_item` WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `purchase_order`      WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `order_oem`           WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `sales_pi_item`       WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `sales_pi`            WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `contract_price`      WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `standard_price`      WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `bom_item`            WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `bom`                 WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `product`             WHERE id BETWEEN 90000 AND 99999;
DELETE FROM `customer`            WHERE id BETWEEN 90000 AND 99999;
