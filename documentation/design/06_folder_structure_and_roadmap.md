# Project Folder Structure and Implementation Roadmap

## 1. Final folder structure
```
foodloop-ai/
├── README.md  .gitignore  .env.example
├── frontend/                (see 02_workflows_and_ui_structure.md)
├── backend/
│   ├── pom.xml  Dockerfile
│   └── src/
│       ├── main/java/com/foodloop/
│       │   ├── FoodLoopApplication.java
│       │   ├── config/        SecurityConfig, CorsConfig, OpenApiConfig, AsyncConfig, SchedulingConfig, AiProperties
│       │   ├── security/      JwtService, JwtAuthFilter, RateLimitFilter, CustomUserDetailsService
│       │   ├── entity/        User, Role, DonorProfile, NgoProfile, VolunteerProfile, Location, FoodCategory, FoodItem,
│       │   │                  Donation, DonationImage, NgoRequirement, DonationMatch, PickupRequest, Delivery,
│       │   │                  Notification, Prediction, Feedback, AuditLog, PasswordResetToken, RevokedToken + enums
│       │   ├── repository/    one Spring Data repository per entity
│       │   ├── dto/           request/ and response/ records
│       │   ├── mapper/        entity <-> DTO mappers
│       │   ├── service/       AuthService, UserService, DonationService, MatchingService, PickupService,
│       │   │                  DeliveryService, NotificationService, EmailService, PredictionService,
│       │   │                  AnalyticsService, ReportService, FeedbackService, AuditService, FileStorageService,
│       │   │                  ExpiryScheduler
│       │   ├── controller/    AuthController, ProfileController, AdminController, DonationController, NgoController,
│       │   │                  VolunteerController, AiController, NotificationController, FeedbackController, FileController
│       │   ├── client/        AiServiceClient, RuleBasedMatcher
│       │   ├── exception/     GlobalExceptionHandler, ResourceNotFoundException, InvalidStatusTransitionException ...
│       │   ├── validation/    @StrongPassword, @FutureExpiry, @ValidImage
│       │   └── util/          GeoUtils (haversine), DonationCodeGenerator, UnitConverter
│       ├── main/resources/    application.yml, application-dev.yml, application-prod.yml
│       └── test/java/com/foodloop/  service/ controller/ security/ integration/
├── ai-service/
│   ├── app.py  config.py  train_model.py  requirements.txt  Dockerfile
│   ├── datasets/   synthetic_donations.csv  synthetic_demand.csv  DATA_DICTIONARY.md  generate_dataset.py
│   ├── models/     (joblib files + metrics.json produced by train_model.py)
│   ├── services/   surplus_service.py demand_service.py matching_service.py analytics_service.py
│   ├── routes/     health_routes.py predict_routes.py recommend_routes.py analytics_routes.py model_routes.py
│   ├── utils/      validation.py auth.py geo.py logging_config.py
│   └── tests/      test_health.py test_predict.py test_recommend.py test_analytics.py
├── database/        schema.sql  sample_data.sql  migrations/  backup/
├── testing/         postman/  reports/  test-plan.md
├── deployment/      docker-compose.yml  nginx.conf  windows-setup.md
├── documentation/   design/  academic/  user-manual/
├── diagrams/  screenshots/
```

## 2. Implementation roadmap with dependencies
| # | Phase | Deliverables | Depends on | Exit check |
|---|---|---|---|---|
| 1-4 | Design (this package) | Architecture, ER, UML/DFD, API contract, schema.sql, sample_data.sql | – | Schema executes on MySQL 8 (done) |
| 5 | MySQL implementation | schema + data, backup scripts | 4 | Counts/constraints verified (done) |
| 6 | Spring Boot core | pom, config, entities, repositories, DTOs, exception handling | 5 | App starts, Hibernate validate passes against schema |
| 7 | Security | JWT, BCrypt, RBAC, revoke list, rate limit, forgot password | 6 | Auth tests, 401/403 tests |
| 8 | Users + donations | profile CRUD, verification, donation CRUD, images, expiry scheduler | 7 | Service + controller tests, Postman |
| 9 | AI service | dataset generator, training, comparison, Flask API, pytest | 4 (contract) | pytest green, metrics.json real |
| 10 | Java<->Python | AiServiceClient, fallback, prediction storage, matching service | 8, 9 | Integration test incl. AI-down case |
| 11 | Pickup/delivery/notifications | pickup, delivery state machine, notifications, email | 10 | E2E workflow via API |
| 12 | Frontend | public pages, 4 dashboards, forms, charts, maps | 7 (auth), 8, 10, 11 APIs | Manual role walk-through |
| 13 | Analytics + reports | dashboards, CSV report | 11 | Numbers match SQL views |
| 14 | Integration testing + security review | E2E, OWASP checks, bug fixes | 12, 13 | Test report from real runs |
| 15 | Deployment | Docker Compose, Windows guide, GitHub | 14 | `docker compose up` works |
| 16 | Academic docs | SRS, report, IEEE paper, survey, PPT, viva | 14 (real results) | Uses only real measured results |

Critical path: 5 -> 6 -> 7 -> 8 -> 10 -> 11 -> 12 -> 14. Phase 9 can run in parallel after the API contract is frozen.

## 3. Rules to stay consistent
Package root `com.foodloop`; enum names exactly as in schema ENUMs; table/column names snake_case, Java camelCase; all timestamps server local time stored as DATETIME; all quantities normalised to kg for matching; AI weights 30/25/15/15/15 identical in Python and Java fallback.
