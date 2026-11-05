-- =====================================================================
-- FoodLoop AI - MySQL 8 schema   (FINAL DESIGN v1.0)
-- Engine: InnoDB | Charset: utf8mb4
-- Run:  mysql -u root -p < schema.sql
-- =====================================================================
DROP DATABASE IF EXISTS foodloop_ai;
CREATE DATABASE foodloop_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE foodloop_ai;

-- ---------- 1. ROLES & USERS ----------
CREATE TABLE roles (
  id TINYINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(20) NOT NULL UNIQUE,            -- ADMIN, DONOR, NGO, VOLUNTEER
  description VARCHAR(150)
) ENGINE=InnoDB;

CREATE TABLE users (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,         -- BCrypt only, never plain text
  full_name VARCHAR(120) NOT NULL,
  phone VARCHAR(15) NOT NULL,
  role_id TINYINT UNSIGNED NOT NULL,
  status ENUM('PENDING','ACTIVE','SUSPENDED','REJECTED') NOT NULL DEFAULT 'PENDING',
  email_verified TINYINT(1) NOT NULL DEFAULT 0,
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id),
  CONSTRAINT chk_users_phone CHECK (phone REGEXP '^[0-9+]{10,15}$'),
  INDEX idx_users_role_status (role_id, status)
) ENGINE=InnoDB;

CREATE TABLE password_reset_tokens (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,         -- SHA-256 of the emailed token
  expires_at DATETIME NOT NULL,
  used TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE revoked_tokens (                  -- JWT logout / invalidation strategy
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  jti VARCHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_revoked_expires (expires_at)
) ENGINE=InnoDB;

-- ---------- 2. LOCATIONS ----------
CREATE TABLE locations (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  address_line VARCHAR(255) NOT NULL,
  area VARCHAR(100),
  city VARCHAR(80) NOT NULL,
  state VARCHAR(80) NOT NULL,
  pincode VARCHAR(10) NOT NULL,
  latitude DECIMAL(9,6) NOT NULL,
  longitude DECIMAL(9,6) NOT NULL,
  CONSTRAINT chk_lat CHECK (latitude BETWEEN -90 AND 90),
  CONSTRAINT chk_lng CHECK (longitude BETWEEN -180 AND 180),
  INDEX idx_loc_city (city),
  INDEX idx_loc_geo (latitude, longitude)
) ENGINE=InnoDB;

-- ---------- 3. ROLE PROFILES (1:1 with users) ----------
CREATE TABLE donor_profiles (
  user_id BIGINT UNSIGNED PRIMARY KEY,
  organization_name VARCHAR(150) NOT NULL,
  donor_type ENUM('RESTAURANT','HOTEL','BAKERY','SUPERMARKET','EVENT_ORGANIZER','OTHER') NOT NULL,
  fssai_license_no VARCHAR(20) NULL,
  location_id BIGINT UNSIGNED NOT NULL,
  verification_status ENUM('PENDING','VERIFIED','REJECTED') NOT NULL DEFAULT 'PENDING',
  verified_by BIGINT UNSIGNED NULL,
  verified_at DATETIME NULL,
  CONSTRAINT fk_dp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_dp_loc FOREIGN KEY (location_id) REFERENCES locations(id),
  CONSTRAINT fk_dp_verifier FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_dp_type (donor_type)
) ENGINE=InnoDB;

CREATE TABLE ngo_profiles (
  user_id BIGINT UNSIGNED PRIMARY KEY,
  organization_name VARCHAR(150) NOT NULL,
  registration_number VARCHAR(50) NOT NULL UNIQUE,
  org_type ENUM('NGO','CHARITY','FOOD_BANK') NOT NULL,
  contact_person VARCHAR(120) NOT NULL,
  location_id BIGINT UNSIGNED NOT NULL,
  daily_capacity_kg DECIMAL(10,2) NOT NULL,
  available_from TIME NOT NULL DEFAULT '08:00:00',
  available_to TIME NOT NULL DEFAULT '21:00:00',
  beneficiaries_served INT UNSIGNED NOT NULL DEFAULT 0,
  verification_status ENUM('PENDING','VERIFIED','REJECTED') NOT NULL DEFAULT 'PENDING',
  verified_by BIGINT UNSIGNED NULL,
  verified_at DATETIME NULL,
  CONSTRAINT fk_np_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_np_loc FOREIGN KEY (location_id) REFERENCES locations(id),
  CONSTRAINT fk_np_verifier FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT chk_np_capacity CHECK (daily_capacity_kg > 0)
) ENGINE=InnoDB;

CREATE TABLE volunteer_profiles (
  user_id BIGINT UNSIGNED PRIMARY KEY,
  vehicle_type ENUM('WALKING','BICYCLE','BIKE','CAR','VAN') NOT NULL,
  service_radius_km DECIMAL(5,1) NOT NULL DEFAULT 10.0,
  availability_status ENUM('AVAILABLE','BUSY','OFFLINE') NOT NULL DEFAULT 'AVAILABLE',
  base_location_id BIGINT UNSIGNED NOT NULL,
  total_deliveries INT UNSIGNED NOT NULL DEFAULT 0,
  CONSTRAINT fk_vp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_vp_loc FOREIGN KEY (base_location_id) REFERENCES locations(id),
  CONSTRAINT chk_vp_radius CHECK (service_radius_km > 0),
  INDEX idx_vp_avail (availability_status)
) ENGINE=InnoDB;

-- ---------- 4. FOOD CATALOGUE ----------
CREATE TABLE food_categories (
  id SMALLINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(60) NOT NULL UNIQUE,
  description VARCHAR(200),
  is_perishable TINYINT(1) NOT NULL DEFAULT 1,
  safe_hours_after_prep SMALLINT UNSIGNED NOT NULL   -- guideline used by expiry monitoring
) ENGINE=InnoDB;

CREATE TABLE food_items (
  id INT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  category_id SMALLINT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL,
  default_unit ENUM('KG','LITRE','PORTIONS','PIECES') NOT NULL,
  kg_per_unit DECIMAL(6,3) NOT NULL DEFAULT 1.000,    -- converts any unit to kg for matching
  CONSTRAINT fk_fi_cat FOREIGN KEY (category_id) REFERENCES food_categories(id),
  UNIQUE KEY uq_fi_name (category_id, name)
) ENGINE=InnoDB;

-- ---------- 5. DONATIONS ----------
CREATE TABLE donations (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  donation_code VARCHAR(20) NOT NULL UNIQUE,          -- e.g. FL-2026-000123
  donor_id BIGINT UNSIGNED NOT NULL,
  category_id SMALLINT UNSIGNED NOT NULL,
  food_item_id INT UNSIGNED NULL,
  food_name VARCHAR(120) NOT NULL,
  quantity DECIMAL(10,2) NOT NULL,
  unit ENUM('KG','LITRE','PORTIONS','PIECES') NOT NULL,
  quantity_kg DECIMAL(10,2) NOT NULL,                 -- normalised quantity
  prepared_at DATETIME NOT NULL,
  expires_at DATETIME NOT NULL,
  storage_condition ENUM('ROOM_TEMP','REFRIGERATED','FROZEN','HOT_HELD') NOT NULL,
  allergens VARCHAR(200),
  is_vegetarian TINYINT(1) NOT NULL DEFAULT 1,
  description VARCHAR(500),
  pickup_location_id BIGINT UNSIGNED NOT NULL,
  pickup_window_start DATETIME NOT NULL,
  pickup_window_end DATETIME NOT NULL,
  safety_declared TINYINT(1) NOT NULL,                -- donor confirms food is safe
  status ENUM('AVAILABLE','MATCHED','ACCEPTED','VOLUNTEER_ASSIGNED','PICKED_UP',
              'IN_TRANSIT','DELIVERED','COMPLETED','CANCELLED','EXPIRED','REJECTED_ALL')
         NOT NULL DEFAULT 'AVAILABLE',
  accepted_ngo_id BIGINT UNSIGNED NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,            -- JPA @Version: blocks double-accept
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_don_donor FOREIGN KEY (donor_id) REFERENCES donor_profiles(user_id),
  CONSTRAINT fk_don_cat FOREIGN KEY (category_id) REFERENCES food_categories(id),
  CONSTRAINT fk_don_item FOREIGN KEY (food_item_id) REFERENCES food_items(id),
  CONSTRAINT fk_don_loc FOREIGN KEY (pickup_location_id) REFERENCES locations(id),
  CONSTRAINT fk_don_ngo FOREIGN KEY (accepted_ngo_id) REFERENCES ngo_profiles(user_id),
  CONSTRAINT chk_don_qty CHECK (quantity > 0 AND quantity_kg > 0),
  CONSTRAINT chk_don_times CHECK (expires_at > prepared_at AND pickup_window_end > pickup_window_start),
  CONSTRAINT chk_don_safety CHECK (safety_declared = 1),
  INDEX idx_don_status_exp (status, expires_at),
  INDEX idx_don_donor (donor_id, created_at),
  INDEX idx_don_cat (category_id),
  INDEX idx_don_ngo (accepted_ngo_id)
) ENGINE=InnoDB;

CREATE TABLE donation_images (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  donation_id BIGINT UNSIGNED NOT NULL,
  stored_file_name CHAR(40) NOT NULL UNIQUE,          -- random UUID name, never the user's name
  original_file_name VARCHAR(150) NOT NULL,
  content_type ENUM('image/jpeg','image/png','image/webp') NOT NULL,
  size_bytes INT UNSIGNED NOT NULL,
  uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_di_don FOREIGN KEY (donation_id) REFERENCES donations(id) ON DELETE CASCADE,
  CONSTRAINT chk_di_size CHECK (size_bytes <= 5242880)
) ENGINE=InnoDB;

-- ---------- 6. NGO REQUIREMENTS & MATCHING ----------
CREATE TABLE ngo_requirements (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  ngo_id BIGINT UNSIGNED NOT NULL,
  category_id SMALLINT UNSIGNED NOT NULL,
  required_quantity_kg DECIMAL(10,2) NOT NULL,
  needed_by DATETIME NOT NULL,
  priority ENUM('LOW','MEDIUM','HIGH','URGENT') NOT NULL DEFAULT 'MEDIUM',
  status ENUM('OPEN','FULFILLED','CLOSED') NOT NULL DEFAULT 'OPEN',
  notes VARCHAR(300),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_nr_ngo FOREIGN KEY (ngo_id) REFERENCES ngo_profiles(user_id) ON DELETE CASCADE,
  CONSTRAINT fk_nr_cat FOREIGN KEY (category_id) REFERENCES food_categories(id),
  CONSTRAINT chk_nr_qty CHECK (required_quantity_kg > 0),
  INDEX idx_nr_open (status, category_id, needed_by)
) ENGINE=InnoDB;

CREATE TABLE donation_matches (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  donation_id BIGINT UNSIGNED NOT NULL,
  ngo_id BIGINT UNSIGNED NOT NULL,
  match_score DECIMAL(5,2) NOT NULL,                  -- 0-100
  distance_km DECIMAL(7,2) NOT NULL,
  rank_position TINYINT UNSIGNED NOT NULL,
  score_breakdown JSON NOT NULL,                      -- explainable factors from AI service
  status ENUM('NOTIFIED','ACCEPTED','REJECTED','EXPIRED','WITHDRAWN') NOT NULL DEFAULT 'NOTIFIED',
  responded_at DATETIME NULL,
  rejection_reason VARCHAR(200),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_dm_don FOREIGN KEY (donation_id) REFERENCES donations(id) ON DELETE CASCADE,
  CONSTRAINT fk_dm_ngo FOREIGN KEY (ngo_id) REFERENCES ngo_profiles(user_id),
  CONSTRAINT chk_dm_score CHECK (match_score BETWEEN 0 AND 100),
  UNIQUE KEY uq_dm (donation_id, ngo_id),             -- duplicate-request prevention
  INDEX idx_dm_ngo_status (ngo_id, status)
) ENGINE=InnoDB;

-- ---------- 7. PICKUP & DELIVERY ----------
CREATE TABLE pickup_requests (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  donation_id BIGINT UNSIGNED NOT NULL UNIQUE,        -- one active pickup per donation
  ngo_id BIGINT UNSIGNED NOT NULL,
  volunteer_id BIGINT UNSIGNED NULL,
  scheduled_pickup_at DATETIME NOT NULL,
  status ENUM('PENDING_ASSIGNMENT','ASSIGNED','VOLUNTEER_ACCEPTED','PICKED_UP','CANCELLED')
         NOT NULL DEFAULT 'PENDING_ASSIGNMENT',
  assigned_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_pr_don FOREIGN KEY (donation_id) REFERENCES donations(id),
  CONSTRAINT fk_pr_ngo FOREIGN KEY (ngo_id) REFERENCES ngo_profiles(user_id),
  CONSTRAINT fk_pr_vol FOREIGN KEY (volunteer_id) REFERENCES volunteer_profiles(user_id),
  INDEX idx_pr_vol_status (volunteer_id, status),
  INDEX idx_pr_status (status)
) ENGINE=InnoDB;

CREATE TABLE deliveries (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  pickup_request_id BIGINT UNSIGNED NOT NULL UNIQUE,
  status ENUM('NOT_STARTED','PICKED_UP','IN_TRANSIT','DELIVERED','RECEIPT_CONFIRMED','FAILED')
         NOT NULL DEFAULT 'NOT_STARTED',
  picked_up_at DATETIME NULL,
  delivered_at DATETIME NULL,
  receipt_confirmed_at DATETIME NULL,
  quantity_received_kg DECIMAL(10,2) NULL,
  last_latitude DECIMAL(9,6) NULL,
  last_longitude DECIMAL(9,6) NULL,
  confirmation_note VARCHAR(300),
  failure_reason VARCHAR(300),
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_del_pr FOREIGN KEY (pickup_request_id) REFERENCES pickup_requests(id),
  INDEX idx_del_status (status)
) ENGINE=InnoDB;

-- ---------- 8. NOTIFICATIONS, PREDICTIONS, FEEDBACK, AUDIT ----------
CREATE TABLE notifications (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  type ENUM('NEW_MATCH','DONATION_ACCEPTED','DONATION_REJECTED','VOLUNTEER_ASSIGNED',
            'PICKUP_UPDATE','DELIVERY_UPDATE','EXPIRY_REMINDER','ACCOUNT_VERIFIED','SYSTEM') NOT NULL,
  title VARCHAR(120) NOT NULL,
  message VARCHAR(400) NOT NULL,
  related_entity_type VARCHAR(30),
  related_entity_id BIGINT UNSIGNED,
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  email_status ENUM('NOT_REQUIRED','PENDING','SENT','FAILED') NOT NULL DEFAULT 'NOT_REQUIRED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_nt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_nt_user_read (user_id, is_read, created_at)
) ENGINE=InnoDB;

CREATE TABLE predictions (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  prediction_type ENUM('SURPLUS','DEMAND','NGO_RECOMMENDATION') NOT NULL,
  donor_id BIGINT UNSIGNED NULL,
  ngo_id BIGINT UNSIGNED NULL,
  donation_id BIGINT UNSIGNED NULL,
  category_id SMALLINT UNSIGNED NULL,
  input_json JSON NOT NULL,
  output_json JSON NOT NULL,
  predicted_value DECIMAL(10,2) NULL,
  model_name VARCHAR(60) NOT NULL,
  model_version VARCHAR(30) NOT NULL,
  trained_on_synthetic TINYINT(1) NOT NULL DEFAULT 1,  -- honesty flag
  latency_ms INT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_pd_donor FOREIGN KEY (donor_id) REFERENCES donor_profiles(user_id) ON DELETE SET NULL,
  CONSTRAINT fk_pd_ngo FOREIGN KEY (ngo_id) REFERENCES ngo_profiles(user_id) ON DELETE SET NULL,
  CONSTRAINT fk_pd_don FOREIGN KEY (donation_id) REFERENCES donations(id) ON DELETE SET NULL,
  CONSTRAINT fk_pd_cat FOREIGN KEY (category_id) REFERENCES food_categories(id),
  INDEX idx_pd_type_date (prediction_type, created_at)
) ENGINE=InnoDB;

CREATE TABLE feedback (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  donation_id BIGINT UNSIGNED NULL,
  from_user_id BIGINT UNSIGNED NOT NULL,
  to_user_id BIGINT UNSIGNED NULL,
  kind ENUM('RATING','COMPLAINT') NOT NULL DEFAULT 'RATING',
  rating TINYINT UNSIGNED NULL,
  comment VARCHAR(500),
  status ENUM('OPEN','IN_REVIEW','RESOLVED') NOT NULL DEFAULT 'OPEN',
  admin_response VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_fb_don FOREIGN KEY (donation_id) REFERENCES donations(id) ON DELETE SET NULL,
  CONSTRAINT fk_fb_from FOREIGN KEY (from_user_id) REFERENCES users(id),
  CONSTRAINT fk_fb_to FOREIGN KEY (to_user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT chk_fb_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
  UNIQUE KEY uq_fb_once (donation_id, from_user_id, kind),
  INDEX idx_fb_status (status)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NULL,
  action VARCHAR(60) NOT NULL,
  entity_type VARCHAR(40) NOT NULL,
  entity_id BIGINT UNSIGNED NULL,
  old_value JSON NULL,
  new_value JSON NULL,
  ip_address VARCHAR(45),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_al_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_al_entity (entity_type, entity_id),
  INDEX idx_al_user_date (user_id, created_at)
) ENGINE=InnoDB;

-- ---------- 9. VIEWS FOR DASHBOARDS / ANALYTICS ----------
CREATE VIEW v_admin_summary AS
SELECT
  (SELECT COUNT(*) FROM users WHERE role_id <> 1)                       AS total_users,
  (SELECT COUNT(*) FROM users WHERE role_id = 2)                        AS total_donors,
  (SELECT COUNT(*) FROM users WHERE role_id = 3)                        AS total_ngos,
  (SELECT COUNT(*) FROM users WHERE role_id = 4)                        AS total_volunteers,
  (SELECT COUNT(*) FROM donations)                                      AS total_donations,
  (SELECT COALESCE(SUM(quantity_kg),0) FROM donations WHERE status='COMPLETED') AS kg_redistributed,
  (SELECT COUNT(*) FROM donations WHERE status IN ('AVAILABLE','MATCHED')) AS pending_donations,
  (SELECT COUNT(*) FROM deliveries WHERE status='RECEIPT_CONFIRMED')    AS completed_deliveries;

CREATE VIEW v_monthly_category_stats AS
SELECT DATE_FORMAT(d.created_at,'%Y-%m') AS month_key,
       c.name AS category,
       COUNT(*) AS donation_count,
       SUM(d.quantity_kg) AS total_kg,
       SUM(CASE WHEN d.status='COMPLETED' THEN d.quantity_kg ELSE 0 END) AS redistributed_kg,
       SUM(CASE WHEN d.status='EXPIRED'   THEN d.quantity_kg ELSE 0 END) AS wasted_kg
FROM donations d JOIN food_categories c ON c.id = d.category_id
GROUP BY month_key, c.name;
