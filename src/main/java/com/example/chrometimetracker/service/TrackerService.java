package com.example.chrometimetracker.service;

import java.time.Instant;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.chrometimetracker.dto.DailySummaryResponse;
import com.example.chrometimetracker.dto.SiteUsageResponse;
import com.example.chrometimetracker.model.ActivityLog;
import com.example.chrometimetracker.repository.ActivityLogRepository;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * サイト切替と時間記録を管理するService。
 */
@Service
public class TrackerService {
	
	private static final Logger LOGGER =
	        LoggerFactory.getLogger(TrackerService.class);

    private static final int MAX_SITE_LENGTH = 255;

    private final ActivityLogRepository activityLogRepository;

    public TrackerService(
            ActivityLogRepository activityLogRepository
    ) {
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * 現在のサイトを更新する。
     *
     * 同じサイトなら何もしない。
     * 別サイトなら前サイトを終了し、新サイトを開始する。
     * siteがnullなら前サイトを終了するだけ。
     */
    @Transactional
    public synchronized void updateContext(
            String rawSite,
            Instant observedAt
    ) {
        String newSite = normalizeSite(rawSite);

        Optional<ActivityLog> openActivity =
                activityLogRepository.findOpenActivity();

        if (openActivity.isPresent()) {
            ActivityLog currentActivity =
                    openActivity.get();

            /*
             * 開始時刻より古いイベントが届いた場合は無視する。
             */
            if (observedAt.isBefore(
                    currentActivity.startedAt()
            )) {
                return;
            }

            /*
             * 現在と同じサイトなら何もしない。
             */
            if (Objects.equals(
                    currentActivity.site(),
                    newSite
            )) {
                return;
            }

            /*
             * 現在のサイトを終了する。
             */
            activityLogRepository.closeActivity(
                    currentActivity.id(),
                    observedAt
            );
        }

        /*
         * nullは「現在の計測対象なし」。
         */
        if (newSite == null) {
            return;
        }

        /*
         * 新サイトの計測開始。
         */
        activityLogRepository.insertActivity(
                newSite,
                observedAt
        );
    }

    /**
     * 指定日のサイト別使用時間を取得する。
     *
     * @param date 集計対象日
     * @return 1日分の集計結果
     */
    public DailySummaryResponse getDailySummary(
            LocalDate date
    ) {
        /*
         * Windowsに設定されているタイムゾーンを使用する。
         * 日本設定ならAsia/Tokyoとして0時境界を計算する。
         */
        ZoneId zoneId = ZoneId.systemDefault();

        Instant dayStart = date
                .atStartOfDay(zoneId)
                .toInstant();

        Instant dayEnd = date
                .plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant();

        List<SiteUsageResponse> sites =
                activityLogRepository.findDailyUsage(
                        dayStart,
                        dayEnd,
                        Instant.now()
                );

        long totalSeconds = sites.stream()
                .mapToLong(SiteUsageResponse::seconds)
                .sum();

        return new DailySummaryResponse(
                date,
                totalSeconds,
                sites
        );
    }
    
    /**
     * 前回正常終了できなかったレコードを、
     * 最終チェックポイント時刻で確定する。
     */
    @PostConstruct
    public void recoverStaleOpenActivities() {
        int recoveredCount =
                activityLogRepository.recoverOpenActivities();

        if (recoveredCount > 0) {
            LOGGER.warn(
                    "前回の未終了レコードを"
                    + "チェックポイント時刻で確定しました: {}件",
                    recoveredCount
            );
        }
    }

    /**
     * Javaアプリの正常終了時に、
     * 現在計測中のレコードを終了する。
     */
    @PreDestroy
    public synchronized void closeCurrentActivityOnShutdown() {
        Instant endedAt = Instant.now();

        try {
            int updatedCount =
                    activityLogRepository.closeOpenActivities(
                            endedAt
                    );

            LOGGER.info(
                    "終了処理で現在の計測を確定しました: {}件",
                    updatedCount
            );
        } catch (RuntimeException exception) {
            /*
             * Windows終了時にPostgreSQLが先に停止していた場合でも、
             * Java自体の終了は妨げない。
             */
            LOGGER.warn(
                    "終了時刻をPostgreSQLへ保存できませんでした。",
                    exception
            );
        }
    }

    /**
     * サイト名をDB保存用に整える。
     */
    private String normalizeSite(String rawSite) {
        if (rawSite == null || rawSite.isBlank()) {
            return null;
        }

        String normalizedSite = rawSite
                .strip()
                .toLowerCase(Locale.ROOT);

        if (normalizedSite.startsWith("www.")) {
            normalizedSite =
                    normalizedSite.substring(4);
        }

        if (normalizedSite.length()
                > MAX_SITE_LENGTH) {
            throw new IllegalArgumentException(
                    "siteは255文字以内で指定してください。"
            );
        }

        return normalizedSite;
    }
    
    /**
     * shutdown時に終了処理が実行されなかった場合に備え、
     * 1分に1回だけ現在時刻を保存する。
     */
    @Scheduled(
            fixedDelay = 60_000,
            initialDelay = 60_000
    )
    public synchronized void checkpointCurrentActivity() {
        activityLogRepository.checkpointOpenActivities(
                Instant.now()
        );
    }
}