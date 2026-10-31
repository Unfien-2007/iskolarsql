-- Presence tracking: the last time an account made an authenticated request.
-- The admin portal treats accounts seen within the last 10 minutes as online.
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;
