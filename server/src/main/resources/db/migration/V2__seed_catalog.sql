-- =====================================================================
-- iskolarPath server catalog seed (PostgreSQL)
-- Mirrors the desktop app's db/seed.sql catalog tables.
-- Deadlines are relative to today so the catalog always shows a
-- realistic urgency spread (green / amber / red / expired).
-- =====================================================================

-- Admin account (password: "password")
INSERT INTO users (email, password_hash, role, is_active) VALUES
  ('admin@iskolar.app',
   '$2a$12$WbQG4q0FI4gJZfa./eRNrOvIDy4R.aXXwSIZ5lHcKSmme4Z9EOKsK',
   'ADMIN', TRUE);

-- ---------------------------------------------------------------------
-- degree_programs
-- ---------------------------------------------------------------------
INSERT INTO degree_programs (program_id, program_code, program_name, field_category) VALUES
  (1,  'BSIT',   'BS Information Technology',      'ICT'),
  (2,  'BSCS',   'BS Computer Science',            'ICT'),
  (3,  'BSCE',   'BS Civil Engineering',           'ENGINEERING'),
  (4,  'BSME',   'BS Mechanical Engineering',      'ENGINEERING'),
  (5,  'BSEE',   'BS Electrical Engineering',      'ENGINEERING'),
  (6,  'BSBA',   'BS Business Administration',     'BUSINESS'),
  (7,  'BSA',    'BS Accountancy',                 'BUSINESS'),
  (8,  'BSN',    'BS Nursing',                     'HEALTH'),
  (9,  'BSPH',   'BS Pharmacy',                    'HEALTH'),
  (10, 'BSBIO',  'BS Biology',                     'SCIENCE'),
  (11, 'BSPSY',  'BS Psychology',                  'SOCIAL_SCIENCE'),
  (12, 'BSED',   'BS Secondary Education',         'EDUCATION'),
  (13, 'BEED',   'BS Elementary Education',        'EDUCATION'),
  (14, 'BSAGRI', 'BS Agriculture',                 'AGRICULTURE'),
  (15, 'BSARCH', 'BS Architecture',                'DESIGN'),
  (16, 'ABCOM',  'AB Communication',               'HUMANITIES');

-- ---------------------------------------------------------------------
-- tags
-- ---------------------------------------------------------------------
INSERT INTO tags (tag_id, tag_code, tag_name, description) VALUES
  (1,  'PWD',               'Person with Disability',           'Registered PWD (with valid PWD ID).'),
  (2,  'SOLO_PARENT_DEP',   'Dependent of a Solo Parent',       'Child/dependent of a recognized solo parent.'),
  (3,  'OFW_DEPENDENT',     'Dependent of an OFW',              'Child/dependent of an overseas Filipino worker.'),
  (4,  'PUBLIC_HS_GRAD',    'Public High School Graduate',      'Graduated from a public senior high school.'),
  (5,  'DOST_PRIORITY',     'DOST Priority S&T Course',         'Enrolled in a DOST-SEI priority science & technology program.'),
  (6,  'VARSITY',           'Varsity Athlete',                  'Accredited varsity athlete of the school.'),
  (7,  'INDIGENOUS_PEOPLE', 'Indigenous Peoples Member',        'Member of an Indigenous Cultural Community.'),
  (8,  '4PS_BENEFICIARY',   '4Ps Beneficiary',                  'Pantawid Pamilyang Pilipino Program beneficiary.'),
  (9,  'VALEDICTORIAN',     'Batch Valedictorian',              'Graduated valedictorian of the senior high school batch.'),
  (10, 'WORKING_STUDENT',   'Working Student',                  'Currently employed while studying.');

-- ---------------------------------------------------------------------
-- document_types
-- ---------------------------------------------------------------------
INSERT INTO document_types (document_type_id, doc_code, doc_name, description) VALUES
  (1, 'GRADES_CTC',          'Certified True Copy of Grades',  'Form 138 / Transcript of Records (CTC).'),
  (2, 'ITR_TAX_EXEMPTION',   'ITR / Tax Exemption',            'Parent''s latest ITR or BIR Certificate of Tax Exemption.'),
  (3, 'BARANGAY_INDIGENCY',  'Barangay Indigency',             'Barangay Certificate of Indigency.'),
  (4, 'GOOD_MORAL',          'Good Moral Certificate',         'Certificate of Good Moral Character.'),
  (5, 'BIRTH_CERTIFICATE',   'PSA Birth Certificate',          'Philippine Statistics Authority birth certificate.'),
  (6, 'ENROLLMENT_FORM',     'Certificate of Enrollment',      'Registration form / certificate of enrollment.'),
  (7, 'ESSAY',               'Personal Essay',                 'Essay / statement of purpose where required.'),
  (8, 'RECOMMENDATION_LETTER','Recommendation Letter',         'Signed recommendation from a teacher/principal.');

-- ---------------------------------------------------------------------
-- scholarships (created_by = 1, the admin)
-- ---------------------------------------------------------------------
INSERT INTO scholarships
  (scholarship_id, created_by, title, provider_name, provider_type, description,
   tuition_benefit, monthly_stipend, return_service_years, application_url,
   open_date, deadline_date, is_active)
VALUES
  (1, 1, 'DOST-SEI S&T Undergraduate Scholarship (RA 7687)',
        'Department of Science and Technology — SEI', 'GOVERNMENT',
        'Flagship S&T scholarship for incoming freshmen in DOST priority courses. Covers full tuition plus a monthly living allowance, with return-of-service in the Philippines after graduation.',
        'FULL TUITION + book allowance', 8000.00, 4,
        'https://www.science-scholarships.ph/',
        (CURRENT_DATE - INTERVAL '60 days')::date, (CURRENT_DATE + INTERVAL '30 days')::date, TRUE),

  (2, 1, 'CHED Merit Scholarship Program (CMSP)',
        'Commission on Higher Education', 'GOVERNMENT',
        'Merit-based national scholarship for top-performing senior high school graduates (GWA 90% and above).',
        'FULL TUITION + book allowance', 2500.00, 2,
        'https://ched.gov.ph/merit-scholarship/',
        (CURRENT_DATE - INTERVAL '60 days')::date, (CURRENT_DATE + INTERVAL '25 days')::date, TRUE),

  (3, 1, 'SM Foundation College Scholarship',
        'SM Foundation, Inc.', 'PRIVATE_CORPORATE',
        'Full tuition scholarship for financially disadvantaged yet academically deserving students nationwide.',
        'FULL TUITION', 2500.00, 0,
        'https://www.sm-foundation.org/',
        (CURRENT_DATE - INTERVAL '75 days')::date, (CURRENT_DATE + INTERVAL '45 days')::date, TRUE),

  (4, 1, 'Aboitiz Foundation College Scholarship',
        'Aboitiz Foundation, Inc.', 'PRIVATE_CORPORATE',
        'College scholarship for high-performing students in business, IT, and engineering programs with a monthly allowance.',
        'FULL TUITION', 3000.00, 0,
        'https://aboitizfoundation.org/',
        (CURRENT_DATE - INTERVAL '60 days')::date, (CURRENT_DATE + INTERVAL '20 days')::date, TRUE),

  (5, 1, 'Iskolar ng Lungsod — Cebu City College Scholarship',
        'Cebu City Government', 'LOCAL_GOVERNMENT',
        'City-funded scholarship exclusively for Cebu City residents enrolled in public schools. Return-of-service: work for the city after graduation.',
        'FULL TUITION (City College)', 1500.00, 2,
        'https://www.cebucity.gov.ph/',
        (CURRENT_DATE - INTERVAL '90 days')::date, (CURRENT_DATE + INTERVAL '5 days')::date, TRUE),

  (6, 1, 'Megaworld Foundation Scholarship',
        'Megaworld Foundation, Inc.', 'PRIVATE_CORPORATE',
        'Full tuition for engineering, architecture, IT, and business students with strong academics and leadership potential.',
        '100% TUITION', 5000.00, 1,
        'https://www.megaworldfoundation.com/',
        (CURRENT_DATE - INTERVAL '60 days')::date, (CURRENT_DATE + INTERVAL '60 days')::date, TRUE),

  (7, 1, 'GSIS Educational Subsidy Program',
        'Government Service Insurance System', 'GOVERNMENT_OWNED_CORP',
        'Annual tuition subsidy for dependents of active GSIS members in good standing.',
        'PHP 40,000 / year tuition subsidy', 0.00, 1,
        'https://www.gsis.gov.ph/',
        (CURRENT_DATE - INTERVAL '90 days')::date, (CURRENT_DATE + INTERVAL '15 days')::date, TRUE),

  (8, 1, 'Security Bank Scholars for Better Communities',
        'Security Bank Foundation, Inc.', 'PRIVATE_CORPORATE',
        'Supports future educators and accountants with full tuition plus allowances through graduation.',
        'FULL TUITION + allowances', 3000.00, 0,
        'https://www.securitybank.com/foundation',
        (CURRENT_DATE - INTERVAL '60 days')::date, (CURRENT_DATE + INTERVAL '2 days')::date, TRUE),

  (9, 1, 'U-Go Scholar Grant (Ayala Foundation)',
        'Ayala Foundation — U-Go', 'NGO_FOUNDATION',
        'Grant for young women pursuing degrees in technology and engineering, with mentorship from industry leaders.',
        'FULL TUITION', 10000.00, 0,
        'https://u-go.scholarship.org/',
        (CURRENT_DATE - INTERVAL '60 days')::date, (CURRENT_DATE + INTERVAL '10 days')::date, TRUE),

  (10, 1, 'Alagang Kapatid Foundation Scholarship',
        'Alagang Kapatid Foundation (TV5)', 'NGO_FOUNDATION',
        'Open scholarship for underprivileged youth; 4Ps beneficiaries and PWDs are prioritized.',
        'FULL TUITION', 2500.00, 0,
        'https://www.alagangkapatid.org/',
        (CURRENT_DATE - INTERVAL '45 days')::date, (CURRENT_DATE + INTERVAL '40 days')::date, TRUE),

  (11, 1, 'CHED Tulong Dunong Program',
        'Commission on Higher Education', 'GOVERNMENT',
        'Semestral grant-in-aid for deserving underprivileged students in any accredited college program.',
        'PHP 15,000 / semester grant', 1250.00, 0,
        'https://ched.gov.ph/tulong-dunong/',
        (CURRENT_DATE - INTERVAL '60 days')::date, (CURRENT_DATE + INTERVAL '12 days')::date, TRUE),

  (12, 1, 'Oblation Scholar Program (UP Diliman)',
        'University of the Philippines Diliman', 'UNIVERSITY',
        'The university''s most prestigious merit award for incoming freshmen with outstanding academic records.',
        'FULL TUITION + allowances', 5000.00, 0,
        'https://upd.edu.ph/',
        (CURRENT_DATE - INTERVAL '45 days')::date, (CURRENT_DATE + INTERVAL '1 day')::date, TRUE),

  (13, 1, 'BPI Foundation Scholarship',
        'BPI Foundation, Inc.', 'PRIVATE_CORPORATE',
        'Scholarship for business and science students. (Deadline intentionally in the past — exercises the Stage 1 deadline gate.)',
        'FULL TUITION', 3000.00, 0,
        'https://www.bpifoundation.com/',
        (CURRENT_DATE - INTERVAL '120 days')::date, (CURRENT_DATE - INTERVAL '10 days')::date, TRUE),

  (14, 1, 'Chevron Scholars Program',
        'Chevron Philippines', 'PRIVATE_CORPORATE',
        'Engineering scholarship for future energy leaders. (Intentionally deactivated — exercises the Stage 1 active gate.)',
        'FULL TUITION', 4000.00, 1,
        'https://philippines.chevron.com/',
        (CURRENT_DATE - INTERVAL '30 days')::date, (CURRENT_DATE + INTERVAL '90 days')::date, FALSE);

-- ---------------------------------------------------------------------
-- scholarship_criteria (1:1)
-- max_annual_income NULL => merit-only. allowed_school_type NULL => both.
-- ---------------------------------------------------------------------
INSERT INTO scholarship_criteria
  (criteria_id, scholarship_id, min_gwa_percentage, allow_failing_grade,
   max_annual_income, allowed_school_type, required_province, required_city)
VALUES
  (1,  1,  83.0, FALSE, 500000.00, NULL,      NULL,   NULL),
  (2,  2,  90.0, FALSE, NULL,       NULL,      NULL,   NULL),
  (3,  3,  88.0, FALSE, 300000.00, NULL,      NULL,   NULL),
  (4,  4,  85.0, FALSE, 280000.00, NULL,      NULL,   NULL),
  (5,  5,  80.0, TRUE,  350000.00, 'PUBLIC',  'CEBU', 'CEBU CITY'),
  (6,  6,  85.0, FALSE, 400000.00, NULL,      NULL,   NULL),
  (7,  7,  85.0, FALSE, 500000.00, NULL,      NULL,   NULL),
  (8,  8,  85.0, FALSE, 350000.00, NULL,      NULL,   NULL),
  (9,  9,  85.0, FALSE, 400000.00, NULL,      NULL,   NULL),
  (10, 10, 80.0, TRUE,  250000.00, NULL,      NULL,   NULL),
  (11, 11, 75.0, TRUE,  300000.00, NULL,      NULL,   NULL),
  (12, 12, 95.0, FALSE, NULL,       NULL,      NULL,   NULL),
  (13, 13, 85.0, FALSE, 350000.00, NULL,      NULL,   NULL),
  (14, 14, 85.0, FALSE, NULL,       NULL,      NULL,   NULL);

-- ---------------------------------------------------------------------
-- scholarship_allowed_programs (empty set = open to ALL programs)
-- ---------------------------------------------------------------------
INSERT INTO scholarship_allowed_programs (scholarship_id, program_id) VALUES
  -- DOST-SEI: priority S&T courses
  (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 9), (1, 10), (1, 14),
  -- Aboitiz: business / IT / engineering
  (4, 1), (4, 2), (4, 3), (4, 4), (4, 5), (4, 6), (4, 7),
  -- Megaworld: engineering / architecture / IT / business
  (6, 1), (6, 2), (6, 3), (6, 6), (6, 7), (6, 15),
  -- Security Bank: education + accountancy
  (8, 7), (8, 12), (8, 13),
  -- U-Go: women in tech
  (9, 1), (9, 2), (9, 5),
  -- Chevron: engineering
  (14, 3), (14, 4), (14, 5);

-- ---------------------------------------------------------------------
-- scholarship_allowed_years (empty set = open to ALL year levels)
-- ---------------------------------------------------------------------
INSERT INTO scholarship_allowed_years (scholarship_id, year_level) VALUES
  (1, 1),
  (2, 1),
  (3, 1),
  (4, 1), (4, 2),
  (5, 1), (5, 2), (5, 3), (5, 4),
  (6, 1), (6, 2),
  (7, 1), (7, 2), (7, 3), (7, 4),
  (8, 1), (8, 2), (8, 3), (8, 4),
  (9, 1), (9, 2), (9, 3),
  (10, 1), (10, 2), (10, 3), (10, 4),
  (11, 1), (11, 2), (11, 3), (11, 4),
  (12, 1),
  (13, 1),
  (14, 1), (14, 2), (14, 3), (14, 4);

-- ---------------------------------------------------------------------
-- scholarship_required_tags (is_mandatory TRUE = hard gate, FALSE = preferred)
-- ---------------------------------------------------------------------
INSERT INTO scholarship_required_tags (scholarship_id, tag_id, is_mandatory) VALUES
  (1,  5, TRUE),   -- DOST: DOST_PRIORITY is a hard requirement
  (9,  1, FALSE),  -- U-Go prefers PWD
  (9,  2, FALSE),  -- U-Go prefers solo-parent dependents
  (10, 8, TRUE),   -- Alagang Kapatid: 4Ps beneficiary is a hard requirement
  (10, 1, FALSE),  -- ...and PWD is preferred
  (12, 9, FALSE);  -- Oblation prefers batch valedictorians

-- ---------------------------------------------------------------------
-- scholarship_documents (required for a complete application)
-- ---------------------------------------------------------------------
INSERT INTO scholarship_documents (scholarship_id, document_type_id) VALUES
  (1, 1), (1, 2), (1, 4), (1, 5), (1, 6), (1, 7),
  (2, 1), (2, 4), (2, 5), (2, 6),
  (3, 1), (3, 2), (3, 4), (3, 5), (3, 6),
  (4, 1), (4, 2), (4, 4), (4, 5),
  (5, 1), (5, 2), (5, 3), (5, 4), (5, 5), (5, 6),
  (6, 1), (6, 2), (6, 4), (6, 5), (6, 6),
  (7, 1), (7, 2), (7, 4), (7, 5), (7, 6),
  (8, 1), (8, 2), (8, 4), (8, 5), (8, 6),
  (9, 1), (9, 2), (9, 4), (9, 5), (9, 6), (9, 7),
  (10, 1), (10, 2), (10, 3), (10, 4), (10, 5), (10, 6),
  (11, 1), (11, 2), (11, 3), (11, 4), (11, 5), (11, 6),
  (12, 1), (12, 4), (12, 5), (12, 6), (12, 7),
  (13, 1), (13, 2), (13, 4), (13, 5),
  (14, 1), (14, 2), (14, 4), (14, 5), (14, 6);

-- ---------------------------------------------------------------------
-- app_state
-- ---------------------------------------------------------------------
INSERT INTO app_state (key, value) VALUES ('catalog_version', '1');
