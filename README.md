# FoodLoop AI - Intelligent Food Waste Prediction and Surplus Food Redistribution Platform

Final-year B.Tech CSBS project. FoodLoop AI connects food donors (hotels, restaurants, bakeries, event caterers) with NGOs and volunteers so that surplus food reaches people before it spoils. A machine-learning service predicts surplus and demand and helps rank which NGO should receive each donation.

**Stack:** HTML/CSS/JS + Bootstrap 5 + Chart.js | Java 17 + Spring Boot 3.3 | MySQL 8 | Python Flask + scikit-learn

## Status

| Area | Status |
|---|---|
| Requirements, architecture, UML/DFD, API contract | Done (`documentation/design`) |
| MySQL schema (21 tables, 2 views) and sample data | Done (`database`) |
| Backend REST API (auth, profiles, donations, matching, pickups, deliveries, admin, stats) | Done, 213 automated tests passing |
| AI service (surplus and demand prediction) | Done, 40 automated tests passing |
| Java to AI integration (fallback, circuit breaker, prediction log) | Done and verified live |
| Frontend: login, register, forgot/reset password, donor, NGO, volunteer and admin pages | Done for the main flows |
| Notifications feature (table exists) | Not implemented |
| Docker and cloud deployment | Not started |

Not built yet: donor donation-details/matches/history pages, NGO requirements/analytics pages, volunteer deliveries page, admin donations/reports/feedback/audit-logs pages.

## How it works

1. A donor posts surplus food (category, quantity, expiry, pickup window).
2. The backend scores every eligible NGO with a rule-based matcher (expiry, capacity, category, distance, quantity). The AI service re-ranks them; if the AI service is down the rule order is used.
3. The best NGO accepts and schedules a pickup. A volunteer accepts the task and moves the delivery through PICKED_UP, IN_TRANSIT and DELIVERED. The NGO confirms the quantity received.
4. Admin sees platform statistics and AI demand forecasts per area (Coimbatore areas in the demo data).

Architecture:

    Browser (static HTML/JS) --JWT--> Spring Boot API :8080 --JDBC--> MySQL
                                           |
                                           +--X-Internal-Api-Key--> Flask AI service :5000

## Run it (Windows 11, PowerShell)

Prerequisites: JDK 17+, Maven, MySQL 8, Python 3.10+.

**Step 1 - create the database and load the schema:**

    mysql -u root -p < database\schema.sql
    mysql -u root -p < database\sample_data.sql
    mysql -u root -p foodloop_ai < database\ngo_accepted_categories.sql

**Step 2 - database user.** Create a MySQL user `foodloop_app` and keep its password outside the repository, in `%USERPROFILE%\.foodloop\db-password.txt` or in the user environment variable `FOODLOOP_DB_PASSWORD`.

**Step 3 - AI service virtual environment (once):**

    cd ai-service
    python -m venv .venv
    .\.venv\Scripts\pip install -r requirements.txt

**Step 4 - start everything:**

    .\start-foodloop.ps1

This starts the AI service (:5000), the backend (:8080) and the frontend (:5500) and opens the browser. Use `-NoAi` to run without the AI service (rule-based matching only).

More detail: [docs/RUN-AND-DEMO.md](docs/RUN-AND-DEMO.md)

## Demo accounts

All sample accounts are fictional and use the password `Password@123` (development only).

| Role | Login |
|---|---|
| Admin | `admin@foodloop.test` |
| Donor | `annapoorna@foodloop.test`, `greenleaf@foodloop.test`, `freshmart@foodloop.test`, `celebration@foodloop.test` |
| NGO | `hopemeals@foodloop.test`, `annadhanam@foodloop.test`, `littlehearts@foodloop.test`, `seva@foodloop.test` |
| Volunteer | `vol.karthik@foodloop.test`, `vol.divya@foodloop.test`, `vol.sanjay@foodloop.test` |

## AI service

- Two scikit-learn RandomForest regressors: donor surplus (kg) and NGO demand (kg) by category, area, weekday, month and hour.
- Trained on **synthetic data**, so reported accuracy describes that data only. The API labels every prediction with `trainedOnSynthetic`.
- The backend calls it with a shared key header, a 1 s connect and 3 s read timeout, one retry on connection failure and a circuit breaker (3 failures pause calls for 30 s). Every prediction, including failures, is stored in the `predictions` table.
- Endpoints: `GET /api/ai/surplus-prediction` (donor), `GET /api/ai/demand-prediction` (NGO, admin), `GET /api/ai/status` (admin).

## Main API

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `login`, `logout`, `forgot-password`, `reset-password` |
| Profile | `GET/PUT /api/profile`, `PUT /api/profile/password` |
| Donations | `POST /api/donations`, `GET /api/donations/mine`, `/available`, `/{id}`, `PUT /{id}`, `POST /{id}/cancel` |
| Matching | `GET /api/donations/{id}/matches`, `GET /api/ngo/recommended`, `POST /api/ngo/matches/{id}/accept`, `/reject` |
| NGO | `GET/PUT /api/ngo/categories`, `POST/GET /api/ngo/pickups`, `POST /api/ngo/deliveries/{id}/confirm`, `GET /api/ngo/history` |
| Volunteer | `GET /api/volunteer/tasks/available`, `/tasks/assigned`, `POST /pickups/{id}/accept`, `PATCH /deliveries/{id}/status`, availability, history |
| Admin | users, user status, verifications (approve/reject), `GET /api/admin/stats` |

The full contract is in `documentation/design/05_api_contract.md`.

## Security

- JWT access tokens (15 minutes), logout revokes the token, role-based access on every endpoint.
- BCrypt password hashing, password policy (8 to 72 characters with upper, lower, digit and special character).
- Password reset tokens are stored hashed and expire after 30 minutes.
- Rate limiting on login (HTTP 429).
- Database credentials are never committed; the start script reads them from the environment or a file outside the repository.

## Tests

- Backend: 213 JUnit/Mockito/Spring Boot tests, all passing (`cd backend`, then `mvn test`).
- AI service: 40 pytest tests, all passing (`cd ai-service`, then `.\.venv\Scripts\python -m pytest`).
- System test log: [documentation/testing/test-report.md](documentation/testing/test-report.md)

## Documentation

Start with `documentation/design/01_requirements_and_architecture.md`, then 02 to 07. Diagrams are Mermaid and render on GitHub or in VS Code Markdown Preview.