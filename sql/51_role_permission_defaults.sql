-- =====================================================================
-- Built-in role permission defaults
-- Snapshot source: production role assignments on 2026-07-22.
-- This script runs after earlier permission migrations so a fresh deploy
-- finishes with the same built-in role assignments as the current server.
-- User-role assignments and non-built-in/custom roles are not modified.
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

DELETE rp
FROM `sys_role_permission` rp
JOIN `sys_role` r ON r.`id` = rp.`role_id`
WHERE r.`id` IN (0, 1, 2, 3, 4, 5)
   OR r.`role_key` IN ('super_admin', 'admin', 'finance', 'sales_manager', 'salesman', 'readonly');

-- super_admin / admin: 105 permissions.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
  ON FIND_IN_SET(CAST(p.`id` AS CHAR),
    '100,101,102,103,104,200,201,202,203,204,300,301,302,303,400,401,500,501,502,503,600,601,602,603,604,700,701,702,900,901,902,903,904,1000,1001,1002,1011,1012,1013,1014,1015,1021,1022,1023,1024,1025,1031,1032,1033,1034,1035,1041,1042,1043,1100,1101,2011,2012,2013,2014,2015,2021,2022,2023,2024,2031,2032,2033,2034,3011,3012,3013,3021,3022,3023,3024,3031,3032,3033,3034,3035,4011,4012,4013,4014,4015,5011,5012,5013,5014,5015,9011,9012,9013,9014,9015,9016,9017,9021,9022,9023,9031,9032,9033,9061') > 0
WHERE r.`id` IN (0, 1)
  AND r.`deleted` = 0
  AND p.`deleted` = 0;

-- finance: 31 permissions.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
  ON FIND_IN_SET(CAST(p.`id` AS CHAR),
    '100,104,200,204,500,501,502,503,600,601,602,603,604,700,701,702,900,1000,1001,1002,1041,1042,1043,1100,1101,5011,5012,5013,5014,5015,9061') > 0
WHERE r.`id` = 2
  AND r.`deleted` = 0
  AND p.`deleted` = 0;

-- sales_manager: 71 permissions.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
  ON FIND_IN_SET(CAST(p.`id` AS CHAR),
    '100,101,102,103,200,201,202,203,204,300,301,302,303,400,401,600,602,604,700,701,702,1000,1001,1002,1011,1012,1013,1014,1015,1021,1022,1023,1024,1025,1031,1032,1033,1034,1035,1100,1101,2011,2012,2013,2014,2015,2021,2022,2023,2024,2031,2032,2033,2034,3011,3012,3013,3021,3022,3023,3024,3031,3032,3033,3034,3035,4011,4012,4013,4014,4015') > 0
WHERE r.`id` = 3
  AND r.`deleted` = 0
  AND p.`deleted` = 0;

-- salesman: 59 permissions.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
  ON FIND_IN_SET(CAST(p.`id` AS CHAR),
    '100,101,102,103,200,201,202,203,204,300,301,302,303,400,401,700,701,702,1000,1001,1002,1011,1012,1013,1014,1015,1021,1024,1025,1031,1100,1101,2011,2014,2015,2022,2023,2024,2031,2032,2033,2034,3011,3012,3013,3021,3022,3023,3024,3031,3032,3033,3034,3035,4011,4012,4013,4014,4015') > 0
WHERE r.`id` = 4
  AND r.`deleted` = 0
  AND p.`deleted` = 0;

-- readonly: 8 permissions.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
  ON FIND_IN_SET(CAST(p.`id` AS CHAR), '100,102,103,104,200,201,700,1100') > 0
WHERE r.`id` = 5
  AND r.`deleted` = 0
  AND p.`deleted` = 0;
