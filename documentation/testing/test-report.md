# FoodLoop AI - Test Report

Generated: 2026-11-22 08:40 (local time)

## 1. Automated backend tests (JUnit 5 + Mockito + Spring Boot test)

Result: **BUILD SUCCESS** - 213 tests, 0 failed/errored, 0 skipped.

| Test class | Tests | Failed | Skipped |
|---|---:|---:|---:|
| entity.DonationStatusAcceptedExpiryTest | 3 | 0 | 0 |
| entity.DonationStatusTest | 4 | 0 | 0 |
| exception.GlobalExceptionHandlerTest | 7 | 0 | 0 |
| security.JwtServiceTest | 6 | 0 | 0 |
| service.AcceptedDonationExpirySchedulerTest | 7 | 0 | 0 |
| service.AdminServiceRoleFilterTest | 3 | 0 | 0 |
| service.AdminServiceTest | 13 | 0 | 0 |
| service.AiPredictionClientTest | 12 | 0 | 0 |
| service.AiPredictionServiceTest | 17 | 0 | 0 |
| service.AiRankerTest | 12 | 0 | 0 |
| service.AuthServiceTest | 15 | 0 | 0 |
| service.DonationExpirySchedulerTest | 6 | 0 | 0 |
| service.DonationServiceTest | 16 | 0 | 0 |
| service.HistoryServiceTest | 6 | 0 | 0 |
| service.MatchingServiceAcceptedCategoriesTest | 4 | 0 | 0 |
| service.MatchingServiceTest | 13 | 0 | 0 |
| service.NgoAcceptedCategoryServiceTest | 7 | 0 | 0 |
| service.PickupCancellationTest | 4 | 0 | 0 |
| service.PickupServiceTest | 17 | 0 | 0 |
| service.RuleBasedMatcherTest | 10 | 0 | 0 |
| service.StatsServiceTest | 7 | 0 | 0 |
| service.VolunteerServiceTest | 7 | 0 | 0 |
| util.DonationCodeGeneratorTest | 2 | 0 | 0 |
| util.GeoUtilsTest | 3 | 0 | 0 |
| util.PasswordPolicyTest | 3 | 0 | 0 |
| util.SlidingWindowRateLimiterTest | 3 | 0 | 0 |
| util.TokenHasherTest | 2 | 0 | 0 |
| util.UnitConverterTest | 4 | 0 | 0 |

## 2. AI service tests (pytest)

40 passed in 2.81s

## 3. System and manual tests (executed against the running stack)

| ID | Area | Scenario | Result |
|---|---|---|---|
| ST-01 | Auth | Log in as donor, NGO, volunteer and admin; role guard redirects to the right dashboard | Pass |
| ST-02 | Auth | Forgot password sends a reset link (dev e-mail log); reset page enforces the password policy; old password stops working | Pass |
| ST-03 | Security | Repeated logins return HTTP 429 (rate limiter) and 401 for an invalid token | Pass |
| ST-04 | Donation | Donor posts 12 donations through the API; matches are created automatically (status MATCHED) | Pass |
| ST-05 | Matching | All 4 NGOs get ranked matches (rank 1 to 4) with score breakdown; rank-1 NGO accepts | Pass |
| ST-06 | Pickup | NGO schedules pickup; volunteer accepts it and a delivery record is created (NOT_STARTED) | Pass |
| ST-07 | Delivery | Volunteer moves delivery PICKED_UP, IN_TRANSIT, DELIVERED; NGO confirms receipt (27.5 of 28 kg) | Pass |
| ST-08 | AI integration | Java calls Flask with the internal API key; prediction is stored in the predictions table | Pass |
| ST-09 | AI fallback | With the AI service stopped, the API returns aiAvailable=false and matching falls back to rule order | Pass |
| ST-10 | AI circuit breaker | After 3 consecutive failures the client pauses calls for 30 seconds | Pass |
| ST-11 | Admin | Admin analytics page loads stats, charts and the AI demand forecast for 8 areas | Pass |
| ST-12 | Secrets | Database password is read from an environment file outside the repository; old password rotated | Pass |

## 4. Notes

- The ML models are trained on synthetic data; accuracy figures describe that data only.
- Integration tests create their own test donations in the development database.
