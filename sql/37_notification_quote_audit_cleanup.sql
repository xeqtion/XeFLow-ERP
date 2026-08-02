-- Normalize legacy sales-quote audit notification types.
-- Older local/dev data may contain custom quote notification types such as QUOTE_AP/QUOTE_APPROVAL.
-- The UI and business rule now treat sales-quote audit notices as SYSTEM notifications.

UPDATE `sys_notification`
SET `type` = 'SYSTEM'
WHERE `type` LIKE 'QUOTE%';
