package com.example.chrometimetracker.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 分析画面で使用するデータ一式。
 */
public record AnalyticsResponse(
        LocalDate date,
        long totalSeconds,
        List<CategoryUsageResponse> categories,
        List<HourlyUsageResponse> hourly,
        WeeklyUsageResponse weekly,
        PeriodComparisonResponse sevenDayComparison,
        ChangeDriversResponse changeDrivers,
        SameWeekdayComparisonResponse sameWeekdayComparison
) {
}