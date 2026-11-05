# FoodLoop AI – Intelligent Food Waste Prediction and Surplus Food Redistribution Platform

Final-year B.Tech CSBS project. Stack: HTML/CSS/JS + Bootstrap 5 + Chart.js | Java 17 + Spring Boot 3 | MySQL 8 | Python Flask + scikit-learn.

## Current status (honest)
| Item | Status |
|---|---|
| Requirement analysis, architecture, workflows, ER/UML/DFD, API contract, roadmap | DONE (documentation/design) |
| MySQL `schema.sql` (20 tables, 2 views) and `sample_data.sql` | DONE – executed on MySQL 8.0.46, constraint checks verified |
| Spring Boot backend | PARTIAL – Phase 6A built + `mvn test` passed on student PC (7 tests); startup + MySQL connection verified. Phase 6B/7 (JWT, BCrypt, RBAC, auth/profile APIs, exception handling, rate limit) written, NOT yet run. Donation/NGO/volunteer APIs not started |
| Flask AI service + dataset + models | NOT STARTED |
| Frontend | NOT STARTED |
| Automated tests, Docker, academic documents | NOT STARTED |
No application tests exist yet, so none are claimed. All sample data is fictional; the AI will be trained on synthetic data and labelled so.

## Quick start (database only, Windows 11)
```
mysql -u root -p < database\schema.sql
mysql -u root -p < database\sample_data.sql
```
Demo password for all sample accounts: `Password@123` (fictional accounts, for development only).

## Docs
Start with `documentation/design/01_requirements_and_architecture.md`, then 02 to 06. Diagrams are Mermaid (render on GitHub or VS Code Markdown Preview).
