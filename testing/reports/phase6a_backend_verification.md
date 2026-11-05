# Phase 6A – Backend foundation verification (what was actually run)
| Check | How | Result |
|---|---|---|
| GeoUtils + DonationStatus logic | Compiled with javac 21 (--release 17) and ran `testing/Check.java` (same assertions as the JUnit tests) | 8/8 PASS. RS Puram->Singanallur = 8.78 km |
| Entities + repositories compile | javac against minimal stub copies of jakarta.persistence, Lombok, Hibernate, Spring Data | OK (20 entity classes). Proves syntax/types only |
| Entity <-> MySQL column mapping | Script compared every entity field with information_schema of the real MySQL 8.0.46 schema | 0 mismatches, no unmapped NOT NULL column without default |

## NOT run (be honest in viva/report)
- `mvn test` / `mvn spring-boot:run`: Maven Central is not reachable from the build sandbox, so the full Spring build, the JUnit run (GeoUtilsTest, DonationStatusTest) and Hibernate startup have NOT been executed. Run `mvn test` on your PC and send me the output; I will fix any error.
- Lombok annotation processing, Spring context startup, JSON column handling with a live MySQL: untested.
