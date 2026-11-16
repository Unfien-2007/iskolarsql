-- Additional curated Philippine scholarship catalog entries.
-- Dates are relative so the demo catalog always has open deadlines.

INSERT INTO scholarships
  (scholarship_id, created_by, title, provider_name, provider_type, description,
   tuition_benefit, monthly_stipend, return_service_years, acceptance_rate,
   awards_available, application_url, open_date, deadline_date, is_active)
VALUES
  (15, 1, 'Metrobank Foundation College Scholarship Program',
        'Metrobank Foundation, Inc.', 'PRIVATE_CORPORATE',
        'Merit grant for academically excellent students from low-income families. Covers tuition for the full degree and includes leadership development activities.',
        'FULL TUITION + allowances', 5000.00, 0, 2.0, 100,
        'https://www.metrobankfoundation.org/', date('now','-30 days'), date('now','+45 days'), 1),

  (16, 1, 'Gokongwei Brothers Foundation Scholarship',
        'Gokongwei Brothers Foundation', 'PRIVATE_CORPORATE',
        'STEM and business grant with mentorship and JG Summit internship opportunities for high-potential students.',
        'FULL TUITION + monthly allowance', 6000.00, 0, 5.0, 250,
        'https://www.gokongweibrothers.com/', date('now','-40 days'), date('now','+60 days'), 1),

  (17, 1, 'Meralco Foundation Scholarship Program',
        'Meralco Foundation, Inc.', 'PRIVATE_CORPORATE',
        'Engineering scholarship for dependents of underserved communities, with practicum support and industry exposure.',
        'FULL TUITION', 4000.00, 0, 8.0, 120,
        'https://www.meralcofoundation.com/', date('now','-25 days'), date('now','+35 days'), 1),

  (18, 1, 'Manila Water Foundation Scholarship',
        'Manila Water Foundation, Inc.', 'NGO_FOUNDATION',
        'Community grant for students in engineering, environment, and IT programs, paired with community service activities.',
        'FULL TUITION + book allowance', 3500.00, 0, 6.0, 150,
        'https://www.manilawaterfoundation.org/', date('now','-20 days'), date('now','+50 days'), 1),

  (19, 1, 'LANDBANK Scholarship Program',
        'Land Bank of the Philippines', 'GOVERNMENT_OWNED_CORP',
        'Support for children of small farmers and fisherfolk pursuing agriculture, agribusiness, and related degrees.',
        'FULL TUITION', 3000.00, 0, 10.0, 500,
        'https://www.landbank.com/', date('now','-15 days'), date('now','+75 days'), 1),

  (20, 1, 'DOST-SEI Junior Level Science Scholarship (JLSS)',
        'Department of Science and Technology - SEI', 'GOVERNMENT',
        'Merit grant for third-year students in DOST priority science and technology courses, with return-of-service in the Philippines.',
        'FULL TUITION + allowances', 7000.00, 3, 6.5, 1000,
        'https://www.science-scholarships.ph/', date('now','-60 days'), date('now','+40 days'), 1),

  (21, 1, 'Department of Agriculture - ACEF Scholarship',
        'Department of Agriculture', 'GOVERNMENT',
        'Agricultural Competitiveness Enhancement Fund grant for agriculture, forestry, fisheries, and agribusiness students, with priority for farmers'' children.',
        'FULL TUITION + stipend', 2500.00, 0, 15.0, 800,
        'https://www.da.gov.ph/', date('now','-35 days'), date('now','+80 days'), 1),

  (22, 1, 'University of Santo Tomas Academic Scholarship',
        'University of Santo Tomas', 'UNIVERSITY',
        'Merit scholarship for outstanding incoming freshmen covering tuition and fees in the first year, renewable based on GWA.',
        'FULL TUITION + fees', 0.00, 0, 3.0, 80,
        'https://www.ust.edu.ph/', date('now','-45 days'), date('now','+21 days'), 1);

INSERT INTO scholarship_criteria
  (criteria_id, scholarship_id, min_gwa_percentage, allow_failing_grade,
   max_annual_income, allowed_school_type, required_province, required_city)
VALUES
  (15, 15, 90.0, 0, 400000.00, NULL, NULL, NULL),
  (16, 16, 88.0, 0, 500000.00, NULL, NULL, NULL),
  (17, 17, 85.0, 0, 450000.00, NULL, NULL, NULL),
  (18, 18, 85.0, 0, 350000.00, NULL, NULL, NULL),
  (19, 19, 80.0, 0, 250000.00, NULL, NULL, NULL),
  (20, 20, 83.0, 0, NULL,       NULL, NULL, NULL),
  (21, 21, 80.0, 1, 300000.00, NULL, NULL, NULL),
  (22, 22, 95.0, 0, NULL,       NULL, NULL, NULL);

-- Empty set = open to ALL programs.
INSERT INTO scholarship_allowed_programs (scholarship_id, program_id) VALUES
  (15, 1), (15, 2), (15, 3), (15, 6), (15, 7),
  (16, 1), (16, 2), (16, 3), (16, 4), (16, 5), (16, 6),
  (17, 3), (17, 4), (17, 5),
  (18, 1), (18, 2), (18, 3), (18, 5), (18, 10),
  (19, 6), (19, 10), (19, 14),
  (20, 1), (20, 2), (20, 3), (20, 4), (20, 5), (20, 9), (20, 10), (20, 14),
  (21, 6), (21, 10), (21, 14);

-- Empty set = open to ALL year levels.
INSERT INTO scholarship_allowed_years (scholarship_id, year_level) VALUES
  (15, 1),
  (16, 1), (16, 2),
  (17, 1), (17, 2), (17, 3),
  (18, 1), (18, 2),
  (19, 1), (19, 2), (19, 3), (19, 4),
  (20, 3),
  (21, 1), (21, 2), (21, 3), (21, 4),
  (22, 1);

INSERT INTO scholarship_required_tags (scholarship_id, tag_id, is_mandatory) VALUES
  (18, 4, 0),   -- Manila Water prefers public high school graduates
  (18, 8, 0),   -- ...and 4Ps beneficiaries
  (19, 8, 0),   -- LANDBANK prefers 4Ps beneficiaries
  (20, 5, 1),   -- DOST JLSS: DOST_PRIORITY is a hard requirement
  (21, 4, 0),   -- DA ACEF prefers public high school graduates
  (21, 8, 0),   -- ...and 4Ps beneficiaries
  (22, 9, 0);   -- UST prefers batch valedictorians

INSERT INTO scholarship_documents (scholarship_id, document_type_id) VALUES
  (15, 1), (15, 2), (15, 4), (15, 5), (15, 6), (15, 8),
  (16, 1), (16, 2), (16, 4), (16, 5), (16, 6),
  (17, 1), (17, 2), (17, 4), (17, 5), (17, 6), (17, 8),
  (18, 1), (18, 2), (18, 3), (18, 4), (18, 5), (18, 6),
  (19, 1), (19, 2), (19, 3), (19, 4), (19, 5), (19, 6),
  (20, 1), (20, 2), (20, 4), (20, 5), (20, 6), (20, 7),
  (21, 1), (21, 2), (21, 3), (21, 4), (21, 5), (21, 6),
  (22, 1), (22, 4), (22, 5), (22, 6), (22, 8);
