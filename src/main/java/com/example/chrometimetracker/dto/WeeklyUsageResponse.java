package com.example.chrometimetracker.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 日曜日から土曜日までの週間利用分布。
 *
 * @param startDate   週の日曜日
 * @param endDate     週の土曜日
 * @param totalSeconds 週間合計秒数
 * @param days        日曜日から土曜日までの7日分
 */
public record WeeklyUsageResponse(
        LocalDate startDate,
        LocalDate endDate,
        long totalSeconds,
        List<WeeklyDayUsageResponse> days
) {
}