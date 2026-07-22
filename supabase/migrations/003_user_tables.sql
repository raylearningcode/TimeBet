-- TimeBet: User sync tables for cross-device support
-- Each table is scoped to auth.uid() via RLS
-- Records use immutable-additive pattern: no balance overwrites

-- ─── User Settings ───
CREATE TABLE IF NOT EXISTS user_settings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    base_daily_allowance_seconds BIGINT NOT NULL DEFAULT 7200,
    onboarding_completed BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id)
);

ALTER TABLE user_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can read own settings" ON user_settings
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can upsert own settings" ON user_settings
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own settings" ON user_settings
    FOR UPDATE USING (auth.uid() = user_id);

CREATE INDEX idx_user_settings_user ON user_settings(user_id);

-- ─── User Time Banks (one per user per date) ───
CREATE TABLE IF NOT EXISTS user_time_banks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    date            DATE NOT NULL,
    base_allowance_seconds  BIGINT NOT NULL DEFAULT 7200,
    current_balance_seconds BIGINT NOT NULL DEFAULT 7200,
    casino_profit_seconds   BIGINT NOT NULL DEFAULT 0,
    casino_loss_seconds     BIGINT NOT NULL DEFAULT 0,
    sports_profit_seconds   BIGINT NOT NULL DEFAULT 0,
    total_win_seconds       BIGINT NOT NULL DEFAULT 0,
    used_seconds            BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, date)
);

ALTER TABLE user_time_banks ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can read own time banks" ON user_time_banks
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can upsert own time banks" ON user_time_banks
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own time banks" ON user_time_banks
    FOR UPDATE USING (auth.uid() = user_id);

CREATE INDEX idx_user_time_banks_user_date ON user_time_banks(user_id, date);

-- ─── User Controlled Apps ───
CREATE TABLE IF NOT EXISTS user_controlled_apps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    package_name    TEXT NOT NULL,
    app_name        TEXT NOT NULL,
    is_controlled   BOOLEAN NOT NULL DEFAULT true,
    device_id       TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, package_name)
);

ALTER TABLE user_controlled_apps ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can read own controlled apps" ON user_controlled_apps
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can upsert own controlled apps" ON user_controlled_apps
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own controlled apps" ON user_controlled_apps
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own controlled apps" ON user_controlled_apps
    FOR DELETE USING (auth.uid() = user_id);

CREATE INDEX idx_user_controlled_apps_user ON user_controlled_apps(user_id);

-- ─── User Casino Rounds (immutable records) ───
CREATE TABLE IF NOT EXISTS user_casino_rounds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    game_type       TEXT NOT NULL,
    stake_seconds   BIGINT NOT NULL,
    profit_seconds  BIGINT NOT NULL DEFAULT 0,
    loss_seconds    BIGINT NOT NULL DEFAULT 0,
    result          TEXT NOT NULL,
    round_metadata  JSONB DEFAULT '{}',
    device_id       TEXT NOT NULL,
    started_at      BIGINT NOT NULL,
    settled_at      BIGINT NOT NULL DEFAULT 0,
    local_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE user_casino_rounds ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can read own casino rounds" ON user_casino_rounds
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own casino rounds" ON user_casino_rounds
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE INDEX idx_user_casino_rounds_user_date ON user_casino_rounds(user_id, local_date);

-- ─── User Usage Sessions (immutable records) ───
CREATE TABLE IF NOT EXISTS user_usage_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    package_name    TEXT NOT NULL,
    duration_seconds BIGINT NOT NULL,
    device_id       TEXT NOT NULL,
    started_at      BIGINT NOT NULL,
    ended_at        BIGINT NOT NULL,
    local_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE user_usage_sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can read own usage sessions" ON user_usage_sessions
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own usage sessions" ON user_usage_sessions
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE INDEX idx_user_usage_sessions_user_date ON user_usage_sessions(user_id, local_date);

-- ─── Updated_at trigger ───
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_user_settings_updated_at') THEN
        CREATE TRIGGER update_user_settings_updated_at
            BEFORE UPDATE ON user_settings
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_user_time_banks_updated_at') THEN
        CREATE TRIGGER update_user_time_banks_updated_at
            BEFORE UPDATE ON user_time_banks
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_user_controlled_apps_updated_at') THEN
        CREATE TRIGGER update_user_controlled_apps_updated_at
            BEFORE UPDATE ON user_controlled_apps
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;
