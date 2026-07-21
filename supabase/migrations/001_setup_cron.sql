-- Enable extensions
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net;

-- Remove old schedules if they exist (ignore errors if not found)
DO $$
BEGIN
    PERFORM cron.unschedule('refresh-fixtures-schedule');
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'refresh-fixtures-schedule not found, skipping';
END $$;

DO $$
BEGIN
    PERFORM cron.unschedule('settle-predictions-schedule');
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'settle-predictions-schedule not found, skipping';
END $$;

-- Refresh fixtures every 6 hours
SELECT cron.schedule(
    'refresh-fixtures-schedule',
    '0 */6 * * *',
    $$
    SELECT net.http_post(
        url:='https://uqffngdjdzdkyqgkamca.supabase.co/functions/v1/refresh-fixtures',
        headers:='{"Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVxZmZuZ2RqZHpka3lxZ2thbWNhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQyMDYwODYsImV4cCI6MjA5OTc4MjA4Nn0.uf2p5WxywzBoSkMG28bvCgHjRJaBcEGTR6QCfzk6BwI"}'::jsonb,
        body:='{}'::jsonb
    );
    $$
);

-- Settle predictions every 10 minutes
SELECT cron.schedule(
    'settle-predictions-schedule',
    '*/10 * * * *',
    $$
    SELECT net.http_post(
        url:='https://uqffngdjdzdkyqgkamca.supabase.co/functions/v1/settle-predictions',
        headers:='{"Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVxZmZuZ2RqZHpka3lxZ2thbWNhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQyMDYwODYsImV4cCI6MjA5OTc4MjA4Nn0.uf2p5WxywzBoSkMG28bvCgHjRJaBcEGTR6QCfzk6BwI"}'::jsonb,
        body:='{}'::jsonb
    );
    $$
);
