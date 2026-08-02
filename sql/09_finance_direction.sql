-- =====================================================================
-- 收付款方向: 借/贷(debit/credit) -> 收/付(in/out)
-- 加载: mysql -u root -proot --default-character-set=utf8mb4 -e "source sql/09_finance_direction.sql"
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

UPDATE `finance_entry` SET `direction` = 'in'  WHERE `direction` = 'debit';
UPDATE `finance_entry` SET `direction` = 'out' WHERE `direction` = 'credit';
