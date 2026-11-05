# Phase 6B/7 – Security & authentication: verification status

## Actually executed (in the build sandbox)
| Check | Result |
|---|---|
| Pure-Java logic: PasswordPolicy, TokenHasher (SHA-256 known vector), SlidingWindowRateLimiter, compiled with javac and run | 15/15 PASS |
| Sample BCrypt hash in sample_data.sql verified against "Password@123" with the bcrypt library | PASS |
| Syntax-only javac pass over all 105 Java files (symbol errors from missing Spring/JWT jars ignored) | No syntax errors found |
| application.yml parses as valid YAML | PASS |

## NOT executed yet – to be run on the student's PC
The sandbox cannot download Spring/JJWT jars, so these have not run anywhere yet:
1. `mvn test` – unit tests: PasswordPolicyTest, TokenHasherTest, SlidingWindowRateLimiterTest, JwtServiceTest, AuthServiceTest (Mockito), plus earlier GeoUtilsTest, DonationStatusTest.
2. `mvn test -Pintegration` – SecurityIntegrationTest against the real MySQL (401/403, login, forged token, logout revocation, validation, duplicate email, registration + login). Rolled back automatically.
3. `mvn spring-boot:run "-Dspring-boot.run.profiles=local"` then Postman collection `testing/postman/foodloop-auth.postman_collection.json`.
Possible runtime surprises to watch for: JSON-column mapping of String fields (audit_logs, predictions) in Hibernate 6.5 – check that audit rows are stored as proper JSON objects, not quoted strings.
