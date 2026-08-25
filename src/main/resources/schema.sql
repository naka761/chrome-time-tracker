CREATE TABLE IF NOT EXISTS activity_log (
    id BIGSERIAL PRIMARY KEY,
    site VARCHAR(255) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,

    CONSTRAINT ck_activity_log_time
        CHECK (
            ended_at IS NULL
            OR ended_at >= started_at
        )
);

-- 既存テーブルへlast_seen_atを追加する
ALTER TABLE activity_log
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

-- 既存レコードを補完する
UPDATE activity_log
SET last_seen_at = COALESCE(ended_at, started_at)
WHERE last_seen_at IS NULL;

ALTER TABLE activity_log
    ALTER COLUMN last_seen_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_activity_log_started_at
    ON activity_log (started_at);