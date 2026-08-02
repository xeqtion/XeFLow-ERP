#!/usr/bin/env bash
set -Eeuo pipefail

# The official MySQL image sources this file and provides docker_process_sql.
# Demo and test datasets remain available in sql/ but are never auto-loaded.
for sql_file in /opt/xeflow/sql/*.sql; do
  case "$(basename "$sql_file")" in
    03_demo.sql|10_test_accounts.sql|31_shipment_test_data.sql|90_test_permission_data.sql|91_test_finance_order_flow.sql|92_finance_notification_test_data.sql)
      echo "[XeFlow] skipping optional dataset: $sql_file"
      continue
      ;;
  esac
  echo "[XeFlow] applying: $sql_file"
  docker_process_sql < "$sql_file"
done
