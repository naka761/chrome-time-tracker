package com.example.chrometimetracker.dto;

/**
 * 1カテゴリー分の使用時間。
 */
public record CategoryUsageResponse(
        String category,
        String label,
        long seconds
) {
}