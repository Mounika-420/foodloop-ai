# Authentication & Profile API (Phase 7)

Base URL `http://localhost:8080`. Errors always use `{timestamp,status,error,message,path,fieldErrors}`.
Password rule: 8-72 chars, upper + lower + digit + special, no spaces. Self-registration roles: DONOR, NGO, VOLUNTEER (ADMIN can never self-register).

| Method + path | Auth | Success | Failure |
|---|---|---|---|
| POST /api/auth/register | public | 201 `{id,email,role,status,message}` (volunteer ACTIVE, donor/NGO PENDING) | 400 validation (fieldErrors), 409 duplicate email / NGO reg. no., 429 rate limit |
| POST /api/auth/login | public | 200 `{accessToken,tokenType:"Bearer",expiresIn:900,user{...}}` | 401 same message for wrong password and unknown email, 403 suspended/rejected, 429 |
| POST /api/auth/logout | Bearer | 204, token jti stored in `revoked_tokens` | – |
| POST /api/auth/forgot-password `{email}` | public | 200 generic message (same for unknown emails) | 400, 429 |
| POST /api/auth/reset-password `{token,newPassword}` | public | 200 | 400 invalid/expired/used token |
| GET /api/profile | Bearer | 200 profile | 401 |
| PUT /api/profile `{fullName,phone}` | Bearer | 200 | 400 |
| PUT /api/profile/password `{currentPassword,newPassword}` | Bearer | 200 | 400 wrong current password |

Role guards: `/api/admin/**` ADMIN, `/api/ngo/**` NGO, `/api/volunteer/**` VOLUNTEER, everything else authenticated. 401 = no/invalid token, 403 = wrong role.

## Windows curl examples (Command Prompt)
```
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"admin@foodloop.test\",\"password\":\"Password@123\"}"
curl http://localhost:8080/api/profile -H "Authorization: Bearer PASTE_TOKEN"
```
Swagger UI: http://localhost:8080/swagger-ui.html

## Security design notes
BCrypt strength 10 | JWT HMAC-SHA, 15 min, unique `jti`, secret from environment (min 32 chars, app refuses to start without it) | logout revocation list | user reloaded on every request (suspension takes effect at once) | reset tokens: 32 random bytes, only SHA-256 stored, 30 min, single use | login/register/forgot/reset limited to 10 requests per minute per IP+endpoint | CORS only for configured origins | stateless, CSRF disabled because no cookies are used | audit_logs rows for register, login, logout, password events | in dev, emails are written to the log unless MAIL_ENABLED=true.
Known limits: rate limiter and revocation check are per-instance/in-DB (fine for a single server); reset token appears in the dev log when email is disabled.
