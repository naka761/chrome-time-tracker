package com.example.chrometimetracker.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.chrometimetracker.model.ActivityInterval;
import com.example.chrometimetracker.model.ActivityLog;

/**
 * activity_logテーブルへのSQLを担当するRepository。
 */
@Repository
public class ActivityLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public ActivityLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 現在計測中のレコードを取得する。
     *
     * @return ended_atがnullのレコード
     */
    public Optional<ActivityLog> findOpenActivity() {
        String sql = """
                SELECT
                    id,
                    site,
                    started_at,
                    ended_at
                FROM activity_log
                WHERE ended_at IS NULL
                ORDER BY started_at DESC, id DESC
                LIMIT 1
                """;

        List<ActivityLog> activities = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new ActivityLog(
                        resultSet.getLong("id"),
                        resultSet.getString("site"),
                        resultSet.getTimestamp("started_at").toInstant(),
                        null
                )
        );

        return activities.stream().findFirst();
    }

    /**
     * 新しいサイトの計測を開始する。
     *
     * @param site      サイト名
     * @param startedAt 開始時刻
     */
    public void insertActivity(
            String site,
            Instant startedAt
    ) {
        String sql = """
                INSERT INTO activity_log (
                    site,
                    started_at,
                    last_seen_at
                )
                VALUES (?, ?, ?)
                """;

        Timestamp timestamp =
                Timestamp.from(startedAt);

        jdbcTemplate.update(
                sql,
                site,
                timestamp,
                timestamp
        );
    }

    /**
     * 現在計測中の全レコードを、指定時刻で終了する。
     *
     * @param endedAt 終了時刻
     * @return 更新件数
     */
    public int closeAllOpenActivities(
            Instant endedAt
    ) {
        String sql = """
                UPDATE activity_log
                SET
                    ended_at = ?,
                    last_seen_at = ?
                WHERE ended_at IS NULL
                  AND started_at <= ?
                """;

        Timestamp timestamp =
                Timestamp.from(endedAt);

        return jdbcTemplate.update(
                sql,
                timestamp,
                timestamp,
                timestamp
        );
    }

    /**
     * 正常終了できず残ったレコードを、
     * 最後のチェックポイント時刻で確定する。
     */
    public int recoverOpenActivities() {
        String sql = """
                UPDATE activity_log
                SET ended_at = last_seen_at
                WHERE ended_at IS NULL
                """;

        return jdbcTemplate.update(sql);
    }

    /**
     * 現在計測中のレコードへチェックポイントを記録する。
     */
    public int checkpointOpenActivities(
            Instant lastSeenAt
    ) {
        String sql = """
                UPDATE activity_log
                SET last_seen_at = ?
                WHERE ended_at IS NULL
                  AND started_at <= ?
                """;

        Timestamp timestamp =
                Timestamp.from(lastSeenAt);

        return jdbcTemplate.update(
                sql,
                timestamp,
                timestamp
        );
    }
    
    /**
     * 指定期間と重なる利用区間を取得する。
     *
     * 現在計測中のレコードは、nowを仮の終了時刻として扱う。
     */
    public List<ActivityInterval> findIntervals(
            Instant rangeStart,
            Instant rangeEnd,
            Instant now
    ) {
        String sql = """
                WITH ordered AS (
                    SELECT
                        id,
                        site,
                        started_at,
                        COALESCE(ended_at, ?) AS effective_end,
                        LAG(COALESCE(ended_at, ?)) OVER (
                            ORDER BY started_at, id
                        ) AS previous_effective_end
                    FROM activity_log
                    WHERE started_at < ?
                )
                SELECT
                    id,
                    site,
                    started_at,
                    effective_end
                FROM ordered
                WHERE effective_end > ?
                   OR previous_effective_end > ?
                ORDER BY started_at, id
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                        new ActivityInterval(
                                resultSet.getLong("id"),
                                resultSet.getString("site"),
                                resultSet
                                        .getTimestamp("started_at")
                                        .toInstant(),
                                resultSet
                                        .getTimestamp("effective_end")
                                        .toInstant()
                        ),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(rangeEnd),
                Timestamp.from(rangeStart),
                Timestamp.from(rangeStart)
        );
    }

    /**
     * 記録を開始した最初の時刻を取得する。
     */
    public Optional<Instant> findFirstStartedAt() {
        Timestamp firstStartedAt =
                jdbcTemplate.queryForObject(
                        """
                        SELECT MIN(started_at)
                        FROM activity_log
                        """,
                        Timestamp.class
                );

        return Optional
                .ofNullable(firstStartedAt)
                .map(Timestamp::toInstant);
    }
}
