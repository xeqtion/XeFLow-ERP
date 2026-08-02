-- Shipment test data.
-- Load with MySQL client using utf8mb4.

DELETE FROM shipment WHERE id BETWEEN 930701 AND 930799;
DELETE FROM shipment WHERE shipment_no IN ('SHP20260706-91001', 'SHP20260706-91002', 'SHP20260706-91003');

INSERT INTO shipment
  (id, shipment_no, si_id, pi_id, ship_date, qty, package_count,
   gross_weight, net_weight, volume_cbm, container_type, container_no, bl_no,
   status, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (930701, 'SHP20260706-91001', 920601, 910501, '2026-08-15', 1100.0000, 1100,
   1260.500, 1188.000, 15.680, '40HQ', 'XFHU9100101', 'BL-XF-91001',
   'SHIPPED', 'Alice Lighting shipment confirmation test data', 910001, NOW(), 910001, NOW(), 0),
  (930702, 'SHP20260706-91002', 920602, 910502, '2026-08-20', 100.0000, 100,
   168.200, 152.400, 2.360, '20GP', 'XFHU9100201', 'BL-XF-91002',
   'DRAFT', 'Nordic Demo shipment draft test data', 910001, NOW(), 910001, NOW(), 0),
  (930703, 'SHP20260706-91003', NULL, 910503, '2026-08-25', 250.0000, 250,
   325.000, 302.500, 4.860, 'AIR', 'AIR-XF-91003', 'AWB-XF-91003',
   'SHIPPED', 'Bob PI-linked shipment test data', 910002, NOW(), 910002, NOW(), 0);
