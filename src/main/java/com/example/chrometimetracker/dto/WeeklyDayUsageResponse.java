package com.example.chrometimetracker.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 週間グラフの1日分。
 *
 * @param date         日付
 * @param dayLabel     日・月・火・水・木・金・土
 * @param totalSeconds その日の合計使用秒数
 * @param categories   カテゴリー別使用時間
 */
public record WeeklyDayUsageResponse(
        LocalDate date,
        String dayLabel,
        long totalSeconds,
        List<CategoryUsageResponse> categories
) {
}