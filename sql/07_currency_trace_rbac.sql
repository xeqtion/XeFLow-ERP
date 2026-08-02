-- =====================================================================
-- 多币种汇率 + 单据追踪 + 角色/权限增删改
-- 加载: mysql -u root -proot --default-character-set=utf8mb4 < sql/07_currency_trace_rbac.sql
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

-- 汇率表: 1 外币 = rate_to_base 本位币(默认 CNY)
CREATE TABLE IF NOT EXISTS `currency_rate` (
  `id`             BIGINT        NOT NULL,
  `currency`       VARCHAR(8)    NOT NULL COMMENT '外币代码',
  `currency_name`  VARCHAR(64)            DEFAULT NULL COMMENT '中文名称(选填)',
  `rate_to_base`   DECIMAL(18,6) NOT NULL COMMENT '1 外币折合的本位币',
  `effective_date` DATE                   DEFAULT NULL COMMENT '生效日期',
  `remark`         VARCHAR(255)           DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cur_date` (`currency`, `effective_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='汇率';

-- (试运营 trial-v1.0: 已移除演示汇率种子数据。汇率可在系统内"汇率管理"维护,
--  或由 erp.currency.online 在线汇率定时任务自动拉取。清理历史演示汇率: )
DELETE FROM `currency_rate` WHERE id BETWEEN 90000 AND 99999;

-- 菜单与按钮权限
INSERT INTO `sys_permission` (`id`,`parent_id`,`perm_name`,`perm_key`,`type`,`path`,`component`,`icon`,`sort`,`status`,`create_time`) VALUES
  -- 基础资料 -> 汇率管理
  (104, 100, '汇率管理', 'currency:list', 'C', '/basedata/currency', 'basedata/currency/index', 'Coin', 14, 1, NOW()),
  (1041,104, '新增汇率', 'currency:create', 'B', NULL, NULL, NULL, 1, 1, NOW()),
  (1042,104, '修改汇率', 'currency:update', 'B', NULL, NULL, NULL, 2, 1, NOW()),
  (1043,104, '删除汇率', 'currency:delete', 'B', NULL, NULL, NULL, 3, 1, NOW()),
  -- 销售管理 -> 单据追踪
  (204, 200, '单据追踪', 'trace:view', 'C', '/sales/trace', 'trace/index', 'Connection', 24, 1, NOW()),
  -- 角色管理 按钮
  (9021,902, '新增角色', 'system:role:create', 'B', NULL, NULL, NULL, 1, 1, NOW()),
  (9022,902, '修改角色', 'system:role:update', 'B', NULL, NULL, NULL, 2, 1, NOW()),
  (9023,902, '删除角色', 'system:role:delete', 'B', NULL, NULL, NULL, 3, 1, NOW()),
  -- 权限菜单 按钮
  (9031,903, '新增权限', 'system:permission:create', 'B', NULL, NULL, NULL, 1, 1, NOW()),
  (9032,903, '修改权限', 'system:permission:update', 'B', NULL, NULL, NULL, 2, 1, NOW()),
  (9033,903, '删除权限', 'system:permission:delete', 'B', NULL, NULL, NULL, 3, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- 超级管理员角色 -> 全部权限 (幂等)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission`
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
