# FoodLoop AI: run and demo guide

FoodLoop AI connects food donors (hotels, restaurants, bakeries) with verified NGOs and volunteers so surplus food reaches people before it expires. NGO ranking uses distance, category, expiry, capacity and quantity, with an AI recommender and a rule-based fallback. The AI models are trained on **synthetic data**, so their predictions are estimates only.

## Architecture

| Part | Tech | Port |
|---|---|---|
| `backend/` | Spring Boot 3.3.5, Java 21, JWT, MySQL | 8080 |
| `ai-service/` | Flask, scikit-learn models | 5000 |
| `frontend/` | HTML, JavaScript, Bootstrap 5.3.3 (no build step) | 5500 |
| `database/` | SQL scripts, database `foodloop_ai` | 3306 |

## Run it (Windows PowerShell)

1. Make sure MySQL is running and the `foodloop_ai` database exists with the scripts in `database/` applied.
2. From the repository root run:

```powershell
.\start-foodloop.ps1          # AI service + backend + frontend, opens the browser
.\start-foodloop.ps1 -NoAi    # skip the AI service, matching uses the rule-based ranking
```

The script waits for `http://localhost:8080/actuator/health` to report `UP`, then opens `http://127.0.0.1:5500/index.html`. Open the frontend only through port 5500, because the backend allows that origin for CORS.

## Demo accounts (password `Password@123`)

| Role | Email |
|---|---|
| Admin | admin@foodloop.test |
| Donor | annapoorna@foodloop.test |
| NGO | annadhanam@foodloop.test, hopemeals@foodloop.test, littlehearts@foodloop.test |

Volunteers and new donors/NGOs can be created on the Register page.

## Demo script (about 8 minutes)

1. **Register** a volunteer on the Register page (role Volunteer, location 11.0168, 76.9558). Log in, the Volunteer dashboard opens.
2. **Donor**: log in as `annapoorna`, open *Add Donation*, post Cooked Meals (for example 5 kg). The ranked NGOs appear immediately with score and distance.
3. **NGO**: log in as `annadhanam`, open *Recommended*, expand *Why this rank?* to see the five score components, then **Accept**. The other NGOs' offers are withdrawn automatically.
4. After Accept the *Pickups* page opens: **schedule the pickup** inside the pickup window.
5. **Volunteer**: log in, open *Tasks*, **Accept task**, then Mark picked up, Start delivery, Mark delivered.
6. **NGO**: open *Pickups*, enter the quantity received and **Confirm receipt**.
7. **Donor**: *My Donations* shows the donation as Completed.
8. **Admin**: the *Dashboard* shows rescued kg, deliveries and the AI service status. *Users* supports search and suspend/activate. *Verification* approves new donors and NGOs.
9. **NGO categories**: open *Categories*, tick only Dairy and Bakery, and Cooked Meals donations stop matching that NGO.

## Feature list

- JWT login with four roles (Donor, NGO, Volunteer, Admin), role-based pages and API access, rate limiting on auth calls, password policy.
- Donations with safety declaration, pickup window and expiry; automatic expiry.
- NGO matching: hard filters (verified and active NGO, opening hours, accepted categories) then a weighted score; AI re-ranking with an automatic rule-based fallback when the AI service is down.
- NGO accept/reject with reason, concurrent accept protection, volunteer task accept with optimistic locking.
- Pickup scheduling, delivery status flow and NGO receipt confirmation with quantity.
- Admin: user management, donor/NGO verification, platform statistics, audit log of important actions (stored in the database).
- Friendly validation errors (`fieldErrors`) and a light/dark theme.

## Tests

```powershell
cd backend
mvn test        # 213 unit tests
```

Live end-to-end checks were run against a real database, including concurrent accept (5 simultaneous requests, one winner), AI service up/down fallback, role filter validation and the accepted-categories feature.

## Known limitations

- AI models use synthetic training data and the recommender currently reproduces the rule-based order.
- Secrets in `start-foodloop.ps1` are local development defaults only. Use real secrets and HTTPS for any deployment.
- Pages not built yet: Donor Matches and History, NGO Requirements and Analytics, Volunteer Deliveries, Admin Donations, Analytics, Reports, Feedback and Audit Logs viewers (the backend already records audit entries), forgot/reset password pages (API exists).

## Setup on a new PC (Windows)

1. Install Git, JDK 17 or newer, Maven 3.9, MySQL 8.0, Python 3.11 and (optionally) VS Code. The first backend start downloads Maven dependencies, so it needs internet.
2. Get the code: extract the submission zip or run `git clone https://github.com/Mounika-420/foodloop-ai.git`. In VS Code use File > Open Folder and choose the repository root (the folder that contains `start-foodloop.ps1`).
3. Load the database from the repository root (enter the MySQL root password when asked). `mysql.exe` is in `C:\Program Files\MySQL\MySQL Server 8.0\bin`, add it to PATH or use the full path:

        mysql -u root -p < database\schema.sql
        mysql -u root -p < database\sample_data.sql
        mysql -u root -p foodloop_ai < database\ngo_accepted_categories.sql

4. Create the application database user with a password of your choice and store it in an environment variable. Open a new terminal afterwards so the variable is visible:

        mysql -u root -p -e "CREATE USER IF NOT EXISTS 'foodloop_app'@'localhost' IDENTIFIED BY 'YOUR_PASSWORD'; GRANT ALL PRIVILEGES ON foodloop_ai.* TO 'foodloop_app'@'localhost'; FLUSH PRIVILEGES;"
        [Environment]::SetEnvironmentVariable('FOODLOOP_DB_PASSWORD', 'YOUR_PASSWORD', 'User')

5. Prepare the AI service once:

        cd ai-service
        python -m venv .venv
        .\.venv\Scripts\python.exe -m pip install -r requirements.txt
        cd ..

   The trained models are included in `ai-service/models/`, so no training step is needed.
6. Start everything from the repository root. If PowerShell blocks the script, run it with `powershell -ExecutionPolicy Bypass -File .\start-foodloop.ps1`:

        .\start-foodloop.ps1

   Then open http://127.0.0.1:5500/index.html and follow the demo script above. The start script uses the local development JWT secret and AI key, replace them for any real deployment.