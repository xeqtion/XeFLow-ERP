-- =====================================================================
-- Revert effects of commit 85914d559046a2f49218192caa989ccae732d19a
-- Removes the collaborative task scheduling permissions and schema fields.
-- Keeps ordinary personal_task rows that do not belong to business workflow tasks.
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

-- Remove business workflow task data introduced by the reverted feature.
DELETE FROM `personal_task`
WHERE `biz_type` IS NOT NULL
   OR `biz_id` IS NOT NULL
   OR `source_task_id` IS NOT NULL;

-- Remove task center permissions/menu entries introduced by the feature.
DELETE FROM `sys_role_permission` WHERE `permission_id` IN (1200, 1201);
DELETE FROM `sys_permission` WHERE `id` IN (1200, 1201) OR `perm_key` IN ('task:pending', 'task:board');

-- Drop indexes added for the business task workflow.
ALTER TABLE `personal_task`
  DROP INDEX `idx_owner_status`,
  DROP INDEX `idx_biz`,
  DROP INDEX `idx_deadline`;

-- Drop columns added for assignment/handover/escalation workflow.
ALTER TABLE `personal_task`
  DROP COLUMN `biz_type`,
  DROP COLUMN `biz_id`,
  DROP COLUMN `biz_no`,
  DROP COLUMN `deadline`,
  DROP COLUMN `assigner_id`,
  DROP COLUMN `source_task_id`,
  DROP COLUMN `escalation_count`,
  DROP COLUMN `escalated_at`,
  DROP COLUMN `reason`,
  DROP COLUMN `handover_log`;
