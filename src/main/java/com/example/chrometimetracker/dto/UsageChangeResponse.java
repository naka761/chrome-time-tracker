package com.example.chrometimetracker.dto;

/**
 * 1カテゴリーまたは1サイトの期間比較結果。
 *
 * @param key               カテゴリーキーまたはドメイン
 * @param label             表示名
 * @param currentSeconds    直近7日の使用秒数
 * @param previousSeconds   前7日の使用秒数
 * @param differenceSeconds 増減秒数
 */
public record UsageChangeResponse(
        String key,
        String label,
        long currentSeconds,
        long previousSeconds,
        long differenceSeconds
) {
}