-- Restore the curated selection estimates for the original 14 catalog entries.
-- These mirror the desktop migration v3. NULL still means "not published";
-- the newer V5 entries are intentionally left unchanged.
UPDATE scholarships SET acceptance_rate = 8.0,  awards_available = 4500 WHERE scholarship_id = 1;
UPDATE scholarships SET acceptance_rate = 12.0, awards_available = 8000 WHERE scholarship_id = 2;
UPDATE scholarships SET acceptance_rate = 4.0,  awards_available = 900  WHERE scholarship_id = 3;
UPDATE scholarships SET acceptance_rate = 3.5,  awards_available = 300  WHERE scholarship_id = 4;
UPDATE scholarships SET acceptance_rate = 18.0, awards_available = 600  WHERE scholarship_id = 5;
UPDATE scholarships SET acceptance_rate = 5.0,  awards_available = 350  WHERE scholarship_id = 6;
UPDATE scholarships SET acceptance_rate = 22.0, awards_available = 2000 WHERE scholarship_id = 7;
UPDATE scholarships SET acceptance_rate = 4.5,  awards_available = 250  WHERE scholarship_id = 8;
UPDATE scholarships SET acceptance_rate = 2.5,  awards_available = 60   WHERE scholarship_id = 9;
UPDATE scholarships SET acceptance_rate = 11.0, awards_available = 120  WHERE scholarship_id = 10;
UPDATE scholarships SET acceptance_rate = 28.0, awards_available = 6000 WHERE scholarship_id = 11;
UPDATE scholarships SET acceptance_rate = 1.5,  awards_available = 90   WHERE scholarship_id = 12;
UPDATE scholarships SET acceptance_rate = 6.0,  awards_available = 180  WHERE scholarship_id = 13;
UPDATE scholarships SET acceptance_rate = 5.5,  awards_available = 120  WHERE scholarship_id = 14;

-- Force already-synced desktop clients to pull the restored values.
UPDATE app_state SET value = (value::int + 1)::text, updated_at = now()
WHERE key = 'catalog_version';
