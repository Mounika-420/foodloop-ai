# Phase 5 – Database verification (actually executed)
Environment: MySQL 8.0.46 (Ubuntu build), fresh data directory, scripts run in order schema.sql -> sample_data.sql.

| Check | Result |
|---|---|
| schema.sql executes without error | PASS – 20 base tables, 2 views |
| sample_data.sql executes without error | PASS |
| v_admin_summary | 11 non-admin users (4 donors, 4 NGOs, 3 volunteers), 13 donations, 115.00 kg redistributed, 2 pending, 5 completed deliveries |
| Donation status spread | AVAILABLE 1, MATCHED 1, ACCEPTED 1, VOLUNTEER_ASSIGNED 1, IN_TRANSIT 1, COMPLETED 5, CANCELLED 1, EXPIRED 2 |
| Pickup NGO equals donation accepted NGO | PASS (0 mismatches) |
| Match score equals sum of breakdown | PASS (0 mismatches) |
| Insert with safety_declared=0 | Correctly REJECTED (chk_don_safety) |
| Duplicate donation+NGO match | Correctly REJECTED (uq_dm) |
| mysqldump of the loaded database | PASS (821 lines) |
Not tested: MySQL 5.7 / MariaDB compatibility, Spring Boot connectivity (backend not built yet).
