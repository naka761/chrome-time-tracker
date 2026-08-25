package com.example.chrometimetracker.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.chrometimetracker.dto.SiteUsageResponse;
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
                ORDER BY started_at DESC
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
     * 計測中レコードの終了時刻を記録する。
     *
     * @param id      activity_logのID
     * @param endedAt 終了時刻
     */
    public void closeActivity(
            long id,
            Instant endedAt
    ) {
        String sql = """
                UPDATE activity_log
                SET
                    ended_at = ?,
                    last_seen_at = ?
                WHERE id = ?
                  AND ended_at IS NULL
                """;

        Timestamp timestamp =
                Timestamp.from(endedAt);

        jdbcTemplate.update(
                sql,
                timestamp,
                timestamp,
                id
        );
    }

    /**
     * 指定された期間と重なるログを、
     * サイトごとに秒数集計する。
     *
     * ended_atがnullの現在計測中レコードは、
     * nowまで使用中として計算する。
     *
     * @param dayStart 対象日の開始時刻
     * @param dayEnd   対象日の翌日開始時刻
     * @param now      集計実行時刻
     * @return サイト別使用時間
     */
    public List<SiteUsageResponse> findDailyUsage(
            Instant dayStart,
            Instant dayEnd,
            Instant now
    ) {
        String sql = """
                SELECT
                    site,
                    CAST(
                        FLOOR(
                            SUM(
                                EXTRACT(
                                    EPOCH FROM (
                                        LEAST(
                                            COALESCE(ended_at, ?),
                                            ?
                                        )
                                        -
                                        GREATEST(
                                            started_at,
                                            ?
                                        )
                                    )
                                )
                            )
                        )
                        AS BIGINT
                    ) AS seconds
                FROM activity_log
                WHERE started_at < ?
                  AND COALESCE(ended_at, ?) > ?
                GROUP BY site
                ORDER BY seconds DESC, site ASC
                """;

        Timestamp nowTimestamp =
                Timestamp.from(now);

        Timestamp dayStartTimestamp =
                Timestamp.from(dayStart);

        Timestamp dayEndTimestamp =
                Timestamp.from(dayEnd);

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                        new SiteUsageResponse(
                                resultSet.getString("site"),
                                resultSet.getLong("seconds")
                        ),

                /*
                 * SQL内の「?」の順番どおりに渡す。
                 */
                nowTimestamp,
                dayEndTimestamp,
                dayStartTimestamp,
                dayEndTimestamp,
                nowTimestamp,
                dayStartTimestamp
        );
    }
    
    /**
     * 現在計測中のレコードを、指定時刻で終了する。
     *
     * @param endedAt 終了時刻
     * @return 更新件数
     */
    public int closeOpenActivities(
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
                SELECT
                    site,
                    started_at,
                    COALESCE(ended_at, ?) AS effective_end
                FROM activity_log
                WHERE started_at < ?
                  AND COALESCE(ended_at, ?) > ?
                ORDER BY started_at
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                        new ActivityInterval(
                                resultSet.getString("site"),
                                resultSet
                                        .getTimestamp("started_at")
                                        .toInstant(),
                                resultSet
                                        .getTimestamp("effective_end")
                                        .toInstant()
                        ),
                Timestamp.from(now),
                Timestamp.from(rangeEnd),
                Timestamp.from(now),
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