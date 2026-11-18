CREATE TABLE IF NOT EXISTS ngo_accepted_categories (
  ngo_id BIGINT UNSIGNED NOT NULL,
  category_id SMALLINT UNSIGNED NOT NULL,
  PRIMARY KEY (ngo_id, category_id),
  KEY idx_nac_cat (category_id),
  CONSTRAINT fk_nac_ngo FOREIGN KEY (ngo_id) REFERENCES ngo_profiles (user_id) ON DELETE CASCADE,
  CONSTRAINT fk_nac_cat FOREIGN KEY (category_id) REFERENCES food_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;