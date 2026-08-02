-- =====================================================================
-- 应收/应付性能: finance_entry 按 related_doc_no 关联单据(PI/PO)做核销聚合,
-- 之前无索引 -> 每次 AR/AP page/summary/aging 全表扫。加索引消除全扫。
-- 加载: mysql -u root -proot --default-character-set=utf8mb4 -e "source sql/18_finance_index.sql"
-- 若索引已存在会报 Duplicate key name, 可安全忽略
-- =====================================================================
USE `erp`;

ALTER TABLE `finance_entry` ADD INDEX `idx_related_doc` (`related_doc_no`);
