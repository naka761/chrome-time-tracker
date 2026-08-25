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

-- 後から開始された行を優先し、既存の重複区間を補正する。
-- 複数の未終了行があっても、最新の1行以外はここで終了する。
WITH ordered AS (
    SELECT
        id,
        started_at,
        ended_at,
        LEAD(started_at) OVER (
            ORDER BY started_at, id
        ) AS next_started_at
    FROM activity_log
)
UPDATE activity_log target
SET
    ended_at = ordered.next_started_at,
    last_seen_at = LEAST(
        target.last_seen_at,
        ordered.next_started_at
    )
FROM ordered
WHERE target.id = ordered.id
  AND ordered.next_started_at IS NOT NULL
  AND (
        ordered.ended_at IS NULL
        OR ordered.ended_at > ordered.next_started_at
  );

CREATE INDEX IF NOT EXISTS idx_activity_log_started_at
    ON activity_log (started_at);

-- 計測中の行はDB全体で最大1件にする。
CREATE UNIQUE INDEX IF NOT EXISTS
    uq_activity_log_single_open
ON activity_log ((1))
WHERE ended_at IS NULL;
