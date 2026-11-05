# FoodLoop AI backend (Spring Boot 3, Java 17)

## Phase 6A contents
pom.xml, application.yml, FoodLoopApplication, 19 enums (incl. DonationStatus state machine), 20 JPA entities, 20 repositories, util (GeoUtils, UnitConverter), unit tests (GeoUtilsTest, DonationStatusTest).
Coming next (6B/7): security (JWT, BCrypt, RBAC), auth, DTOs, services, controllers, exception handling.

## Run on Windows 11 (needs JDK 17, Maven, MySQL with database loaded)
```
cd backend
set DB_USER=root
set DB_PASSWORD=your_mysql_password
set JWT_SECRET=<base64 string, >= 32 bytes>
mvn test
mvn spring-boot:run
```
The app has no controllers yet, so it only starts and connects to MySQL. Entities were cross-checked against the real MySQL schema; `ddl-auto` is `none` because schema.sql owns the schema.
