package com.example.chrometimetracker.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 1日分のChrome使用時間。
 *
 * @param date         集計対象日
 * @param totalSeconds 全サイトの合計秒数
 * @param sites        サイト別の使用時間
 */
public record DailySummaryResponse(
        LocalDate date,
        long totalSeconds,
        List<SiteUsageResponse> sites
) {
}