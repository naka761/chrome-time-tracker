package com.example.chrometimetracker.dto;

import java.time.LocalDate;

/**
 * 直近7日間と、その前の7日間の比較。
 */
public record PeriodComparisonResponse(
        LocalDate currentStartDate,
        LocalDate currentEndDate,
        long currentSeconds,

        LocalDate previousStartDate,
        LocalDate previousEndDate,
        long previousSeconds,

        long differenceSeconds,
        Double differencePercent
) {
}