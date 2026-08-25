package com.example.chrometimetracker.model;

import java.time.Instant;

/**
 * 分析用の1利用区間。
 *
 * @param id        activity_logのID
 * @param site      サイト
 * @param startedAt 開始時刻
 * @param endedAt   分析上の終了時刻
 */
public record ActivityInterval(
        long id,
        String site,
        Instant startedAt,
        Instant endedAt
) {
}
