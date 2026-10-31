-- =====================================================================
-- iskolarPath: Discover, match, and track your scholarship journey.
-- test_profiles.sql  |  sample student profiles for automated tests
-- =====================================================================
-- Production seed data no longer ships demo student accounts. The tests
-- still need realistic profiles (with tags, documents, and tracker
-- applications), so those rows live here and are loaded by TestDb only.
-- Depends on seed.sql (degree programs, tags, document types, scholarships).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. demo users (test-only) — profile.user_id references these rows
-- ---------------------------------------------------------------------
INSERT INTO users (user_id, email, password_hash, role, theme_pref) VALUES
  (2, 'maria.santos@email.com',  '$2a$12$WbQG4q0FI4gJZfa./eRNrOvIDy4R.aXXwSIZ5lHcKSmme4Z9EOKsK', 'STUDENT', 'LIGHT'),
  (3, 'juan.delacruz@email.com', '$2a$12$WbQG4q0FI4gJZfa./eRNrOvIDy4R.aXXwSIZ5lHcKSmme4Z9EOKsK', 'STUDENT', 'LIGHT');

-- ---------------------------------------------------------------------
-- 1. student_profiles
-- Maria: BSIT Y1, GWA 91.50 (≈1.34), public HS, Laguna, income 240k
-- Juan:  BSCE Y1, GWA 86.25 (≈1.55), public HS, Cebu City, income 180k
-- ---------------------------------------------------------------------
INSERT INTO student_profiles
  (student_id, user_id, first_name, last_name, birthdate, sex, province,
   city_municipality, school_name, school_type, program_id, year_level,
   gwa_percentage, has_failing_grade, annual_family_income, household_size)
VALUES
  (1, 2, 'Maria', 'Santos', '2007-04-15', 'FEMALE', 'LAGUNA', 'SANTA ROSA',
        'Santa Rosa Science and Technology High School', 'PUBLIC', 1, 1,
        91.50, 0, 240000.00, 5),
  (2, 3, 'Juan', 'Dela Cruz', '2006-11-02', 'MALE', 'CEBU', 'CEBU CITY',
        'Abellana National School', 'PUBLIC', 3, 1,
        86.25, 0, 180000.00, 7);

-- ---------------------------------------------------------------------
-- 2. student_tags
-- ---------------------------------------------------------------------
INSERT INTO student_tags (student_id, tag_id) VALUES
  (1, 4), (1, 5), (1, 2),          -- Maria: PUBLIC_HS_GRAD, DOST_PRIORITY, SOLO_PARENT_DEP
  (2, 1), (2, 8), (2, 3), (2, 4);  -- Juan: PWD, 4PS_BENEFICIARY, OFW_DEPENDENT, PUBLIC_HS_GRAD

-- ---------------------------------------------------------------------
-- 3. student_documents (Document Readiness Vault)
-- Maria: everything ready EXCEPT ITR -> exercises "missing document" warning
-- Juan:  everything ready EXCEPT birth certificate
-- ---------------------------------------------------------------------
INSERT INTO student_documents (student_id, document_type_id, is_ready) VALUES
  (1, 1, 1), (1, 2, 0), (1, 3, 1), (1, 4, 1), (1, 5, 1), (1, 6, 1),
  (2, 1, 1), (2, 2, 1), (2, 3, 1), (2, 4, 1), (2, 5, 0), (2, 6, 1);

-- ---------------------------------------------------------------------
-- 4. applications (Kanban tracker)
-- ---------------------------------------------------------------------
INSERT INTO applications
  (student_id, scholarship_id, status, applied_date, notes)
VALUES
  (1, 1,  'SAVED',          NULL,             'Bookmarked while reviewing DOST requirements.'),
  (1, 3,  'PREPARING_DOCS', NULL,             'Gathering ITR from parent.'),
  (1, 2,  'APPLIED',        date('now','-20 days'), 'Submitted via CHED portal.'),
  (1, 11, 'APPLIED',        date('now','-15 days'), 'Submitted via school registrar.'),
  (2, 5,  'APPLIED',        date('now','-10 days'), 'LGU walk-in application.'),
  (2, 10, 'INTERVIEW',      NULL,             'Interview scheduled with Alagang Kapatid.'),
  (2, 1,  'SAVED',          NULL,             'Checking DOST priority course requirement.');
