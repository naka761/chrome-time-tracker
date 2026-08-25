package com.example.chrometimetracker.dto;

/**
 * 1サイト分の使用時間。
 *
 * @param site    サイト名
 * @param seconds 対象日の使用秒数
 */
public record SiteUsageResponse(
        String site,
        long seconds
) {
}