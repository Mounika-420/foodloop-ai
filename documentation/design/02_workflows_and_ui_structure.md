# Role Workflows and Frontend Page Structure (FINAL v1.0)

## 1. Role workflows
### Donor
Register -> (admin verifies) -> login -> Dashboard -> Add donation (form shows AI surplus prediction) -> submit -> system matches NGOs -> donor sees ranked NGOs + scores -> NGO accepts -> volunteer assigned -> live tracking -> COMPLETED -> rate NGO. Can cancel until PICKED_UP.

### NGO
Register with registration number -> admin verifies -> login -> post requirements -> receive NEW_MATCH notification -> open donation (score breakdown, distance, expiry countdown) -> Accept / Reject(reason) -> set pickup time -> track volunteer -> confirm receipt with quantity received -> rate donor.

### Volunteer
Register -> login -> set availability -> see assigned + nearby available tasks (within service radius) -> accept -> navigate (map link) -> Picked up -> In transit -> Delivered (with location) -> history.

### Admin
Login -> verify pending donors/NGOs -> monitor donations -> manage users (suspend/reactivate) -> analytics + reports -> complaints -> audit logs.

## 2. Frontend folder and pages
```
frontend/
  index.html                      landing (hero, about, how it works, features, contact CTA)
  css/ theme.css (green/white/dark tokens, dark mode) | components.css | dashboard.css
  js/  config.js | api.js (fetch wrapper, JWT, 401 handling) | auth.js | guard.js (role guard)
       ui.js (toasts, spinners, confirm modal, empty states) | validators.js | theme.js
       charts.js | map.js (configurable provider + fallback) | notifications.js
  pages/public/   about.html how-it-works.html features.html contact.html
                  login.html register.html forgot-password.html reset-password.html
  pages/donor/    dashboard.html add-donation.html my-donations.html donation-details.html
                  matches.html notifications.html profile.html history.html
  pages/ngo/      dashboard.html available-donations.html recommended.html requirements.html
                  pickups.html history.html analytics.html notifications.html profile.html
  pages/volunteer/dashboard.html tasks.html task-details.html deliveries.html
                  history.html notifications.html profile.html
  pages/admin/    dashboard.html users.html verification.html donations.html
                  analytics.html reports.html feedback.html audit-logs.html
  assets/images, assets/icons
```
Shared layout: top navbar (theme toggle, notification bell with unread count, profile menu) + role-specific sidebar (collapses to off-canvas on mobile). Every page: loading skeleton, empty state, error alert, toast on success.

## 3. Navigation per role
- Donor: Dashboard, Add Donation, My Donations, Matches, History, Notifications, Profile
- NGO: Dashboard, Available, Recommended, Requirements, Pickups, History, Analytics, Notifications, Profile
- Volunteer: Dashboard, Tasks, Deliveries, History, Notifications, Profile
- Admin: Dashboard, Users, Verification, Donations, Analytics, Reports, Feedback, Audit Logs

## 4. Design tokens
Primary green #2E7D32, light green #E8F5E9, accent #66BB6A, dark neutral #1F2933, surface #FFFFFF; dark mode swaps surface to #121816 and text to #E6EDE9. Font: Inter (fallback system-ui). 8-px spacing scale; cards radius 12 px; WCAG AA contrast; focus outlines kept visible.
