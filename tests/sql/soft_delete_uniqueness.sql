-- Execute after sql/52_soft_delete_unique_keys.sql, using the same target DB.
-- Only session-local TEMPORARY tables are written; never alters real accounts.
-- A test runner must fail unless every result line starts with PASS (14 checks).
CREATE TEMPORARY TABLE regression_soft_delete_user LIKE sys_user;
CREATE TEMPORARY TABLE regression_soft_delete_role LIKE sys_role;

INSERT INTO regression_soft_delete_user (id, username, password, deleted)
VALUES (101, 'regression-user', 'not-a-login-hash', 1),
       (102, 'regression-user', 'not-a-login-hash', 0);
UPDATE regression_soft_delete_user SET deleted=1 WHERE id=102 AND deleted=0;
SELECT IF(COUNT(*)=2, 'PASS user second deletion preserves history', 'FAIL user second deletion')
FROM regression_soft_delete_user WHERE username='regression-user' AND deleted=1;

INSERT INTO regression_soft_delete_user (id, username, password) VALUES (103, 'regression-user', 'not-a-login-hash');
UPDATE regression_soft_delete_user SET deleted=1 WHERE id=103 AND deleted=0;
SELECT IF(COUNT(*)=3, 'PASS user third deletion preserves history', 'FAIL user third deletion')
FROM regression_soft_delete_user WHERE username='regression-user' AND deleted=1;

INSERT INTO regression_soft_delete_user (id, username, password) VALUES (104, 'regression-user', 'not-a-login-hash');
INSERT IGNORE INTO regression_soft_delete_user (id, username, password) VALUES (105, 'regression-user', 'not-a-login-hash');
GET DIAGNOSTICS CONDITION 1 @duplicate_errno = MYSQL_ERRNO;
SELECT IF(@duplicate_errno=1062 AND (SELECT COUNT(*) FROM regression_soft_delete_user WHERE id=105)=0,
  'PASS duplicate active username rejected', 'FAIL duplicate active username');

UPDATE regression_soft_delete_user SET status=0 WHERE id=104;
INSERT IGNORE INTO regression_soft_delete_user (id, username, password) VALUES (106, 'regression-user', 'not-a-login-hash');
GET DIAGNOSTICS CONDITION 1 @duplicate_errno = MYSQL_ERRNO;
SELECT IF(@duplicate_errno=1062 AND (SELECT COUNT(*) FROM regression_soft_delete_user WHERE id=106)=0,
  'PASS disabled username still reserved', 'FAIL disabled username reservation');

UPDATE IGNORE regression_soft_delete_user SET deleted=0 WHERE id=101;
GET DIAGNOSTICS CONDITION 1 @duplicate_errno = MYSQL_ERRNO;
SELECT IF(@duplicate_errno=1062 AND (SELECT deleted FROM regression_soft_delete_user WHERE id=101)=1,
  'PASS conflicting user restore rejected', 'FAIL conflicting user restore');

INSERT INTO regression_soft_delete_user (id, username, password) VALUES (107, 'another-user', 'not-a-login-hash');
UPDATE IGNORE regression_soft_delete_user SET username='regression-user' WHERE id=107;
GET DIAGNOSTICS CONDITION 1 @duplicate_errno = MYSQL_ERRNO;
SELECT IF(@duplicate_errno=1062 AND (SELECT username FROM regression_soft_delete_user WHERE id=107)='another-user',
  'PASS conflicting username rename rejected', 'FAIL conflicting username rename');
SELECT IF(COUNT(*)=3, 'PASS deleted users excluded from unique key', 'FAIL deleted user unique markers')
FROM regression_soft_delete_user WHERE deleted=1 AND active_unique IS NULL;

INSERT INTO regression_soft_delete_role (id, role_name, role_key, deleted)
VALUES (201, 'Regression', 'regression-role', 1), (202, 'Regression', 'regression-role', 0);
UPDATE regression_soft_delete_role SET deleted=1 WHERE id=202 AND deleted=0;
SELECT IF(COUNT(*)=2, 'PASS role second deletion preserves history', 'FAIL role second deletion')
FROM regression_soft_delete_role WHERE role_key='regression-role' AND deleted=1;

INSERT INTO regression_soft_delete_role (id, role_name, role_key) VALUES (203, 'Regression', 'regression-role');
UPDATE regression_soft_delete_role SET deleted=1 WHERE id=203 AND deleted=0;
SELECT IF(COUNT(*)=3, 'PASS role third deletion preserves history', 'FAIL role third deletion')
FROM regression_soft_delete_role WHERE role_key='regression-role' AND deleted=1;

INSERT INTO regression_soft_delete_role (id, role_name, role_key) VALUES (204, 'Regression', 'regression-role');
INSERT IGNORE INTO regression_soft_delete_role (id, role_name, role_key) VALUES (205, 'Regression', 'regression-role');
GET DIAGNOSTICS CONDITION 1 @duplicate_errno = MYSQL_ERRNO;
SELECT IF(@duplicate_errno=1062 AND (SELECT COUNT(*) FROM regression_soft_delete_role WHERE id=205)=0,
  'PASS duplicate active role key rejected', 'FAIL duplicate active role key');

UPDATE regression_soft_delete_role SET status=0 WHERE id=204;
INSERT IGNORE INTO regression_soft_delete_role (id, role_name, role_key) VALUES (206, 'Regression', 'regression-role');
GET DIAGNOSTICS CONDITION 1 @duplicate_errno = MYSQL_ERRNO;
SELECT IF(@duplicate_errno=1062 AND (SELECT COUNT(*) FROM regression_soft_delete_role WHERE id=206)=0,
  'PASS disabled role key still reserved', 'FAIL disabled role reservation');

UPDATE IGNORE regression_soft_delete_role SET deleted=0 WHERE id=201;
GET DIAGNOSTICS CONDITION 1 @duplicate_errno = MYSQL_ERRNO;
SELECT IF(@duplicate_errno=1062 AND (SELECT deleted FROM regression_soft_delete_role WHERE id=201)=1,
  'PASS conflicting role restore rejected', 'FAIL conflicting role restore');

INSERT INTO regression_soft_delete_role (id, role_name, role_key) VALUES (207, 'Another', 'another-role');
UPDATE IGNORE regression_soft_delete_role SET role_key='regression-role' WHERE id=207;
GET DIAGNOSTICS CONDITION 1 @duplicate_errno = MYSQL_ERRNO;
SELECT IF(@duplicate_errno=1062 AND (SELECT role_key FROM regression_soft_delete_role WHERE id=207)='another-role',
  'PASS conflicting role key rename rejected', 'FAIL conflicting role key rename');
SELECT IF(COUNT(*)=3, 'PASS deleted roles excluded from unique key', 'FAIL deleted role unique markers')
FROM regression_soft_delete_role WHERE deleted=1 AND active_unique IS NULL;

DROP TEMPORARY TABLE regression_soft_delete_user;
DROP TEMPORARY TABLE regression_soft_delete_role;
