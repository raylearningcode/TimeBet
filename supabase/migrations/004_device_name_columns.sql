-- Add device_name columns for per-device visibility
ALTER TABLE user_usage_sessions ADD COLUMN IF NOT EXISTS device_name TEXT DEFAULT 'Unknown Device';
ALTER TABLE user_casino_rounds ADD COLUMN IF NOT EXISTS device_name TEXT DEFAULT 'Unknown Device';
ALTER TABLE user_controlled_apps ADD COLUMN IF NOT EXISTS device_name TEXT DEFAULT 'Unknown Device';
