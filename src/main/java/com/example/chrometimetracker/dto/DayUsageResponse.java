package com.example.chrometimetracker.dto;

import java.time.LocalDate;

/**
 * 比較対象となる1日分の使用時間。
 */
public record DayUsageResponse(
        LocalDate date,
        long seconds
) {
}