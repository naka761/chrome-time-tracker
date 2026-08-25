package com.example.chrometimetracker.dto;

/**
 * Chrome拡張から送られる現在のサイト情報。
 *
 * @param site       現在のサイト。nullの場合は計測終了
 * @param observedAt イベント発生時刻（Unix time milliseconds）
 */
public record ContextRequest(
        String site,
        long observedAt
) {
}