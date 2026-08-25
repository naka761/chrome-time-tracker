package com.example.chrometimetracker.dto;

import java.util.List;

/**
 * 対象日と、過去の同曜日中央値の比較。
 */
public record SameWeekdayComparisonResponse(
        long targetSeconds,
        Long medianSeconds,
        Long differenceSeconds,
        Double differencePercent,
        List<DayUsageResponse> samples
) {
}