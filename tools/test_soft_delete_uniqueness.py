#!/usr/bin/env python3
"""Exercise fresh/legacy MySQL 8 schemas in an isolated, disposable test database.

Usage: python3 tools/test_soft_delete_uniqueness.py -- mysql -uroot
Or:    python3 tools/test_soft_delete_uniqueness.py -- docker exec -i MYSQL_ID mysql -uroot
Credentials belong in MYSQL_PWD/client config, not in this file or command arguments.
The client must be able to create/drop the randomly named test database.
"""
import argparse
from pathlib import Path
import re
import subprocess
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mysql_command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.mysql_command
    if command[:1] == ['--']:
        command = command[1:]
    if not command:
        command = ['mysql']
    command += ['--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names']
    root = Path(__file__).resolve().parent.parent
    schema = (root / 'sql/01_schema.sql').read_text(encoding='utf-8')
    migration = (root / 'sql/52_soft_delete_unique_keys.sql').read_text(encoding='utf-8')
    regression = (root / 'tests/sql/soft_delete_uniqueness.sql').read_text(encoding='utf-8')
    database = 'xeflow_soft_delete_test_' + uuid.uuid4().hex
    created = False

    def mysql(sql, *, select_database=True, check=True):
        argv = command + (['--database=' + database] if select_database else [])
        result = subprocess.run(argv, input=sql, text=True, encoding='utf-8',
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)
        if check and result.returncode:
            raise RuntimeError(result.stderr.strip())
        return result

    def table_ddl(table):
        match = re.search(r'CREATE TABLE IF NOT EXISTS `' + table + r'` \(.*?;(?=\s|$)', schema, re.S)
        if not match:
            raise RuntimeError('Missing table DDL: ' + table)
        return match.group(0)

    def regression_checks(label):
        lines = mysql(regression).stdout.splitlines()
        if len(lines) != 14 or any(not line.startswith('PASS ') for line in lines):
            raise RuntimeError('Regression failed: ' + '\n'.join(lines))
        print(f'PASS {label}: {len(lines)} checks', flush=True)

    def snapshot():
        return mysql('SELECT id,username,password,status,deleted FROM sys_user ORDER BY id;'
                     'SELECT id,role_name,role_key,status,deleted FROM sys_role ORDER BY id;').stdout

    try:
        mysql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;',
              select_database=False)
        created = True
        for table, column in [('sys_user', 'username'), ('sys_role', 'role_key')]:
            # Reconstruct the exact pre-patch index without loading business/demo data.
            legacy = re.sub(r'^.*`active_unique` TINYINT.*\n', '', table_ddl(table), flags=re.M)
            legacy = legacy.replace(f'(`{column}`, `active_unique`)', f'(`{column}`, `deleted`)')
            mysql(legacy)
        mysql("INSERT INTO sys_user(id,username,password,deleted) VALUES"
              "(1,'history-user','not-a-login-hash',1),(2,'history-user','not-a-login-hash',0);"
              "INSERT INTO sys_role(id,role_name,role_key,deleted) VALUES"
              "(1,'History','history-role',1),(2,'History','history-role',0);")
        for table in ['sys_user', 'sys_role']:
            result = mysql(f'UPDATE `{table}` SET deleted=1 WHERE id=2;', check=False)
            if result.returncode == 0 or 'ERROR 1062' not in result.stderr:
                raise RuntimeError('Legacy failure not reproduced: ' + table)
            print('PASS legacy duplicate-key bug reproduced: ' + table, flush=True)
        before = snapshot()
        mysql(migration)
        if snapshot() != before:
            raise RuntimeError('Migration modified existing user or role data')
        print('PASS migration preserves account and role rows', flush=True)
        regression_checks('upgraded schema')
        ddl_before = mysql('SHOW CREATE TABLE sys_user; SHOW CREATE TABLE sys_role;').stdout
        mysql(migration)
        if mysql('SHOW CREATE TABLE sys_user; SHOW CREATE TABLE sys_role;').stdout != ddl_before:
            raise RuntimeError('Repeated migration changed table structure')
        regression_checks('repeated migration')
        mysql('RENAME TABLE sys_user TO upgraded_user, sys_role TO upgraded_role;')
        mysql(table_ddl('sys_user') + '\n' + table_ddl('sys_role'))
        mysql(migration)
        regression_checks('fresh installation')
        print('PASS all soft-delete migration tests', flush=True)
    finally:
        if created:
            # Only this runner's exact, newly created test database may be dropped.
            if not re.fullmatch(r'xeflow_soft_delete_test_[0-9a-f]{32}', database):
                raise RuntimeError('Unsafe cleanup target')
            mysql(f'DROP DATABASE `{database}`;', select_database=False)
            print('PASS disposable test database removed', flush=True)


if __name__ == '__main__':
    main()
