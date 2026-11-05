# API Contracts (FINAL v1.0)

Conventions: base `/api`; JSON; `Authorization: Bearer <jwt>`; dates ISO-8601; lists paged with `?page=0&size=10&sort=createdAt,desc`; paged response `{content:[], page, size, totalElements, totalPages}`; errors `{timestamp, status, error, message, path, fieldErrors:{field:msg}}`.

## A. Spring Boot REST endpoints (browser -> Java)
| Area | Method + Path | Role | Notes |
|---|---|---|---|
| Auth | POST /api/auth/register | public | role DONOR/NGO/VOLUNTEER only; status PENDING (volunteer ACTIVE) |
| | POST /api/auth/login | public | returns accessToken, expiresIn, user{id,name,role} |
| | POST /api/auth/logout | any | revokes jti |
| | POST /api/auth/forgot-password, /reset-password | public | token emailed (console fallback in dev) |
| Profile | GET/PUT /api/profile | any | role-specific fields |
| | PUT /api/profile/password | any | needs current password |
| Admin | GET /api/admin/users ?role&status&q | ADMIN | |
| | PATCH /api/admin/users/{id}/status | ADMIN | ACTIVE/SUSPENDED |
| | POST /api/admin/verifications/{userId}/approve, /reject | ADMIN | donors + NGOs |
| | GET /api/admin/dashboard, /donations, /feedback, /audit-logs | ADMIN | |
| | GET /api/admin/reports/donations.csv ?from&to | ADMIN | |
| Donations | POST /api/donations (multipart: `data` JSON + `images[]`) | DONOR | runs AI + matching |
| | GET /api/donations/mine ?status&q | DONOR | |
| | GET /api/donations/{id} | owner/NGO matched/volunteer assigned/ADMIN | |
| | PUT /api/donations/{id} | DONOR | only AVAILABLE |
| | POST /api/donations/{id}/cancel | DONOR | before PICKED_UP |
| | GET /api/donations/available ?category&q&maxDistanceKm | NGO | |
| | GET /api/donations/{id}/matches | DONOR/ADMIN | ranked NGOs |
| NGO | GET /api/ngo/recommended | NGO | donations matched to me |
| | POST /api/ngo/matches/{matchId}/accept, /reject {reason} | NGO | optimistic lock |
| | GET/POST/PUT/DELETE /api/ngo/requirements | NGO | |
| | POST /api/ngo/pickups {donationId, scheduledPickupAt} | NGO | |
| | POST /api/ngo/deliveries/{id}/confirm {quantityReceivedKg, note} | NGO | -> COMPLETED |
| | GET /api/ngo/history, /analytics | NGO | |
| Volunteer | GET /api/volunteer/tasks/available, /tasks/assigned | VOLUNTEER | |
| | POST /api/volunteer/pickups/{id}/accept | VOLUNTEER | |
| | PATCH /api/volunteer/deliveries/{id}/status {status, lat, lng} | VOLUNTEER | forward-only |
| | PUT /api/volunteer/availability | VOLUNTEER | |
| | GET /api/volunteer/history | VOLUNTEER | |
| AI (proxy) | GET /api/ai/surplus-prediction ?category&date | DONOR | |
| | GET /api/ai/demand-prediction ?category&area&date | NGO/ADMIN | |
| | GET /api/ai/analytics/waste-patterns | ADMIN | |
| | GET /api/ai/status | ADMIN | health + model info |
| Notifications | GET /api/notifications ?unread, PATCH /{id}/read, POST /read-all | any | |
| Feedback | POST /api/feedback | DONOR/NGO | after COMPLETED |
| Files | GET /api/files/{storedFileName} | authenticated | |
| Docs | /swagger-ui.html, /v3/api-docs | public in dev | |

**Example – login**
Request `{"email":"hopemeals@foodloop.test","password":"Password@123"}`
Response `{"accessToken":"<jwt>","tokenType":"Bearer","expiresIn":900,"user":{"id":6,"fullName":"Priya Dharshini","role":"NGO"}}`

## B. Flask AI service (Java -> Python), base `http://localhost:5000`
Header on every call except /health: `X-Internal-Api-Key: <AI_SERVICE_API_KEY>` -> 401 otherwise. Timeouts: connect 1 s, read 3 s. Every response includes `"modelVersion"` and `"trainedOnSyntheticData": true`.

### GET /health
`{"status":"UP","modelsLoaded":{"surplus":true,"demand":true}}`

### POST /api/v1/predict/surplus
```json
{"category":"Cooked Meals","donorType":"HOTEL","dayOfWeek":"MON","month":11,
 "season":"WINTER","eventFlag":false,"previousQuantityKg":22.0,"area":"RS Puram"}
```
Response `{"predictedSurplusKg":19.4,"confidenceNote":"model MAE on synthetic test set: see /models/info","modelName":"RandomForestRegressor","modelVersion":"1.0.0","trainedOnSyntheticData":true}`

### POST /api/v1/predict/demand
Request `{"category":"Cooked Meals","area":"Ukkadam","dayOfWeek":"MON","month":11,"hourOfDay":18}` -> `{"predictedDemandKg":..., "modelName":..., ...}`

### POST /api/v1/recommend/ngos
Request (Java supplies candidates, Flask holds no DB):
```json
{"donation":{"donationId":4,"category":"Cooked Meals","quantityKg":36.0,"latitude":11.001,"longitude":76.975,
             "expiresAt":"2026-11-03T21:00:00","hoursToExpiry":3.0},
 "candidates":[{"ngoId":6,"latitude":10.999,"longitude":77.03,"remainingCapacityKg":150.0,
   "acceptedCategories":["Cooked Meals","Dairy"],"openRequirementKg":40.0,
   "availableFrom":"08:00","availableTo":"21:00"}],
 "topN":5}
```
Response:
```json
{"recommendations":[{"ngoId":6,"rank":1,"matchScore":87.4,"distanceKm":9.8,
  "breakdown":{"distance":26.1,"category":25.0,"quantity":13.5,"capacity":12.8,"expiry":10.0},
  "explanation":"Close by, accepts Cooked Meals, capacity covers 36 kg, reachable before expiry"}],
 "method":"weighted-scoring","weights":{"distance":30,"category":25,"quantity":15,"capacity":15,"expiry":15}}
```
Score = sum of five factor points (max 30/25/15/15/15 = 100). Distance by haversine. NGOs outside operating hours or unable to arrive before expiry are excluded. (Numbers shown in sample_data.sql are illustrative seeds, not computed output.)

### POST /api/v1/analytics/waste-patterns
Java sends rows of `v_monthly_category_stats`: `{"rows":[{"month":"2026-10","category":"Fruits & Vegetables","totalKg":20,"redistributedKg":0,"wastedKg":20}]}` -> `{"topWastedCategories":[...],"monthlyTrend":[...],"insights":["..."]}`

### GET /api/v1/models/info
`{"models":[{"name":"surplus","algorithm":"RandomForestRegressor","version":"1.0.0","trainedAt":"...","metrics":{"mae":..,"rmse":..,"r2":..},"dataset":"synthetic"}]}` – metrics are read from the file written by `train_model.py`; never hand-typed.

### Error contract (Flask)
400 `{"error":"VALIDATION_ERROR","details":{"field":"msg"}}`, 401, 503 `{"error":"MODEL_NOT_LOADED"}`, 500 `{"error":"PREDICTION_FAILED"}`.

## C. Java <-> Python integration rules
1. `AiServiceClient` uses Spring `RestClient` with timeouts; 1 retry on connect error only.
2. On timeout / 5xx / open circuit: surplus -> `null` + `aiAvailable=false` flag (form still works); matching -> Java `RuleBasedMatcher` (same formula, same weights) and `predictions.model_name='rule-based-fallback'`.
3. Every call stored in `predictions` incl. latency_ms.
4. Config: `ai.service.base-url`, `ai.service.api-key` from environment variables.
