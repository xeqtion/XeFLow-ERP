-- PI lifecycle: DRAFT -> CONFIRMED -> ORDERED -> SHIPPED -> COMPLETED.
USE `erp`;
SET NAMES utf8mb4;

UPDATE `sales_pi` SET `status` = 'COMPLETED' WHERE `status` = 'CLOSED';

UPDATE `sales_pi` p
JOIN (
  SELECT `related_doc_no`, SUM(`amount`) AS `deposit_received`
  FROM `finance_entry`
  WHERE `deleted` = 0
    AND `related_doc_type` = 'PI'
    AND `direction` = 'in'
    AND `type` = 'PRE_RECEIVE'
  GROUP BY `related_doc_no`
) receipt ON receipt.`related_doc_no` = p.`pi_no`
SET p.`status` = 'ORDERED'
WHERE p.`deleted` = 0
  AND p.`status` = 'CONFIRMED'
  AND receipt.`deposit_received` > 0
  AND (p.`deposit_amount` <= 0 OR receipt.`deposit_received` >= p.`deposit_amount`);
