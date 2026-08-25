package com.example.chrometimetracker.model;

import java.time.Instant;

/**
 * activity_logテーブルの1行を表す。
 *
 * @param id        記録ID
 * @param site      サイト名
 * @param startedAt 開始時刻
 * @param endedAt   終了時刻。計測中ならnull
 */
public record ActivityLog(
        long id,
        String site,
        Instant startedAt,
        Instant endedAt
) {
}