package com.example.chrometimetracker.dto;

import java.util.List;

/**
 * 1時間帯分のカテゴリー別使用時間。
 */
public record HourlyUsageResponse(
        int hour,
        long totalSeconds,
        List<CategoryUsageResponse> categories
) {
}