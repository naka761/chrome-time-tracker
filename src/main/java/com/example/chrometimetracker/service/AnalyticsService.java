package com.example.chrometimetracker.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.example.chrometimetracker.dto.AnalyticsResponse;
import com.example.chrometimetracker.dto.CategoryUsageResponse;
import com.example.chrometimetracker.dto.DayUsageResponse;
import com.example.chrometimetracker.dto.HourlyUsageResponse;
import com.example.chrometimetracker.dto.PeriodComparisonResponse;
import com.example.chrometimetracker.dto.SameWeekdayComparisonResponse;
import com.example.chrometimetracker.dto.WeeklyDayUsageResponse;
import com.example.chrometimetracker.dto.WeeklyUsageResponse;
import com.example.chrometimetracker.model.ActivityInterval;
import com.example.chrometimetracker.model.UsageCategory;
import com.example.chrometimetracker.repository.ActivityLogRepository;
import com.example.chrometimetracker.dto.ChangeDriversResponse;
import com.example.chrometimetracker.dto.UsageChangeResponse;

/**
 * スクリーンタイムの比較・時間帯分析を担当する。
 */
@Service
public class AnalyticsService {

    private static final int HOURS_PER_DAY = 24;

    private final ActivityLogRepository activityLogRepository;
    private final SiteCategoryClassifier categoryClassifier;
    private final ActivityIntervalNormalizer intervalNormalizer;

    public AnalyticsService(
            ActivityLogRepository activityLogRepository,
            SiteCategoryClassifier categoryClassifier,
            ActivityIntervalNormalizer intervalNormalizer
    ) {
        this.activityLogRepository =
                activityLogRepository;

        this.categoryClassifier =
                categoryClassifier;

        this.intervalNormalizer =
                intervalNormalizer;
    }

    public AnalyticsResponse getAnalytics(
            LocalDate targetDate
    ) {
        ZoneId zoneId = ZoneId.systemDefault();
        Instant now = Instant.now();

        /*
         * 選択した1日の範囲。
         */
        ZonedDateTime targetDayStartZoned =
                targetDate.atStartOfDay(zoneId);

        ZonedDateTime targetFullDayEndZoned =
                targetDate
                        .plusDays(1)
                        .atStartOfDay(zoneId);

        Instant targetDayStart =
                targetDayStartZoned.toInstant();

        Instant targetDayEnd =
                determineEffectiveDayEnd(
                        targetDayStartZoned,
                        targetFullDayEndZoned,
                        now
                );

        /*
         * 選択日を含む日曜日～土曜日。
         */
        LocalDate weekStartDate =
                targetDate.with(
                        TemporalAdjusters.previousOrSame(
                                DayOfWeek.SUNDAY
                        )
                );

        LocalDate weekEndDate =
                weekStartDate.plusDays(6);

        ZonedDateTime weekStartZoned =
                weekStartDate.atStartOfDay(zoneId);

        ZonedDateTime weekExclusiveEndZoned =
                weekStartDate
                        .plusDays(7)
                        .atStartOfDay(zoneId);

        /*
         * 現在の週なら未来分は含めず、現在時刻まで。
         * 過去の週なら土曜日の24時まで。
         */
        Instant effectiveWeekEnd =
                determineEffectiveDayEnd(
                        weekStartZoned,
                        weekExclusiveEndZoned,
                        now
                );

        /*
         * 直近7日間。
         */
        ZonedDateTime currentSevenDayStartZoned =
                targetDate
                        .minusDays(6)
                        .atStartOfDay(zoneId);

        ZonedDateTime currentSevenDayEndZoned =
                targetDayEnd.atZone(zoneId);

        ZonedDateTime previousSevenDayStartZoned =
                currentSevenDayStartZoned
                        .minusWeeks(1);

        ZonedDateTime previousSevenDayEndZoned =
                currentSevenDayEndZoned
                        .minusWeeks(1);

        /*
         * 過去4回の同曜日比較に必要な最古の日付。
         */
        Instant fourWeeksAgoStart =
                targetDate
                        .minusWeeks(4)
                        .atStartOfDay(zoneId)
                        .toInstant();

        Instant queryStart = earlierOf(
                fourWeeksAgoStart,
                previousSevenDayStartZoned.toInstant()
        );

        /*
         * 週間グラフの日曜日がさらに前なら、
         * そこからログを取得する。
         */
        queryStart = earlierOf(
                queryStart,
                weekStartZoned.toInstant()
        );

        /*
         * 選択日だけでなく、
         * その週の土曜日分まで取得する。
         */
        Instant queryEnd = laterOf(
                targetDayEnd,
                effectiveWeekEnd
        );

        List<ActivityInterval> intervals =
                intervalNormalizer.normalize(
                        activityLogRepository.findIntervals(
                                queryStart,
                                queryEnd,
                                now
                        )
                );

        DayBreakdown dayBreakdown =
                createDayBreakdown(
                        intervals,
                        targetDayStart,
                        targetDayEnd,
                        zoneId
                );

        WeeklyUsageResponse weeklyUsage =
                createWeeklyUsage(
                        intervals,
                        weekStartDate,
                        zoneId,
                        now
                );

        PeriodComparisonResponse sevenDayComparison =
                createSevenDayComparison(
                        intervals,
                        targetDate,
                        currentSevenDayStartZoned,
                        currentSevenDayEndZoned,
                        previousSevenDayStartZoned,
                        previousSevenDayEndZoned
                );
        
        ChangeDriversResponse changeDrivers =
                createChangeDrivers(
                        intervals,
                        targetDate,
                        currentSevenDayStartZoned,
                        currentSevenDayEndZoned,
                        previousSevenDayStartZoned,
                        previousSevenDayEndZoned
                );

        SameWeekdayComparisonResponse
                sameWeekdayComparison =
                createSameWeekdayComparison(
                        intervals,
                        targetDate,
                        targetDayStart,
                        targetDayEnd,
                        zoneId,
                        dayBreakdown.totalSeconds()
                );

        return new AnalyticsResponse(
                targetDate,
                dayBreakdown.totalSeconds(),
                dayBreakdown.categories(),
                dayBreakdown.hourly(),
                weeklyUsage,
                sevenDayComparison,
                changeDrivers,
                sameWeekdayComparison
        );
    }

    /**
     * 対象日が今日なら現在時刻まで、
     * 過去日なら24時まで、
     * 未来日なら0秒として扱う。
     */
    private Instant determineEffectiveDayEnd(
            ZonedDateTime dayStart,
            ZonedDateTime fullDayEnd,
            Instant now
    ) {
        Instant start = dayStart.toInstant();
        Instant end = fullDayEnd.toInstant();

        if (now.isBefore(start)) {
            return start;
        }

        if (now.isBefore(end)) {
            return now;
        }

        return end;
    }

    /**
     * ①カテゴリー別合計
     * ②24時間カテゴリー別集計
     */
    private DayBreakdown createDayBreakdown(
            List<ActivityInterval> intervals,
            Instant dayStart,
            Instant dayEnd,
            ZoneId zoneId
    ) {
        List<Map<UsageCategory, Long>> hourlyMillis =
                new ArrayList<>();

        for (int hour = 0;
                hour < HOURS_PER_DAY;
                hour++) {

            hourlyMillis.add(
                    new EnumMap<>(UsageCategory.class)
            );
        }

        long totalMillis = 0;

        for (ActivityInterval interval : intervals) {
            Instant clippedStart = laterOf(
                    interval.startedAt(),
                    dayStart
            );

            Instant clippedEnd = earlierOf(
                    interval.endedAt(),
                    dayEnd
            );

            if (!clippedStart.isBefore(clippedEnd)) {
                continue;
            }

            UsageCategory category =
                    categoryClassifier.classify(
                            interval.site()
                    );

            long intervalMillis =
                    Duration
                            .between(
                                    clippedStart,
                                    clippedEnd
                            )
                            .toMillis();

            totalMillis += intervalMillis;

            splitIntervalIntoHours(
                    clippedStart,
                    clippedEnd,
                    category,
                    hourlyMillis,
                    zoneId
            );
        }

        List<Map<UsageCategory, Long>> hourlySeconds =
                roundHourlyCategoryMillis(
                        hourlyMillis,
                        totalMillis
                );

        Map<UsageCategory, Long> dayCategorySeconds =
                new EnumMap<>(UsageCategory.class);

        List<HourlyUsageResponse>
                hourlyResponses =
                new ArrayList<>();

        long dayTotalSeconds = 0;

        for (int hour = 0;
                hour < HOURS_PER_DAY;
                hour++) {

            Map<UsageCategory, Long> hourData =
                    hourlySeconds.get(hour);

            long hourTotalSeconds =
                    hourData
                            .values()
                            .stream()
                            .mapToLong(Long::longValue)
                            .sum();

            if (hourTotalSeconds > 3600) {
                throw new IllegalStateException(
                        "1時間枠の集計が3600秒を超えました。"
                );
            }

            hourData.forEach(
                    (category, seconds) ->
                            dayCategorySeconds.merge(
                                    category,
                                    seconds,
                                    Long::sum
                            )
            );

            dayTotalSeconds += hourTotalSeconds;

            hourlyResponses.add(
                    new HourlyUsageResponse(
                            hour,
                            hourTotalSeconds,
                            createCategoryResponses(
                                    hourData
                            )
                    )
            );
        }

        if (dayTotalSeconds != totalMillis / 1000) {
            throw new IllegalStateException(
                    "日合計と24時間枠の合計が一致しません。"
            );
        }

        if (dayTotalSeconds > 86400) {
            throw new IllegalStateException(
                    "1日合計が86400秒を超えました。"
            );
        }

        List<CategoryUsageResponse>
                categoryResponses =
                createCategoryResponses(
                        dayCategorySeconds
                );

        return new DayBreakdown(
                dayTotalSeconds,
                categoryResponses,
                hourlyResponses
        );
    }

    /**
     * 利用区間を1時間単位へ切り分ける。
     */
    private void splitIntervalIntoHours(
            Instant intervalStart,
            Instant intervalEnd,
            UsageCategory category,
            List<Map<UsageCategory, Long>>
                    hourlyMillis,
            ZoneId zoneId
    ) {
        Instant cursor = intervalStart;

        while (cursor.isBefore(intervalEnd)) {
            ZonedDateTime cursorZoned =
                    cursor.atZone(zoneId);

            ZonedDateTime nextHourZoned =
                    cursorZoned
                            .withMinute(0)
                            .withSecond(0)
                            .withNano(0)
                            .plusHours(1);

            Instant segmentEnd = earlierOf(
                    intervalEnd,
                    nextHourZoned.toInstant()
            );

            long segmentMillis =
                    Duration
                            .between(cursor, segmentEnd)
                            .toMillis();

            int hour = cursorZoned.getHour();

            hourlyMillis
                    .get(hour)
                    .merge(
                            category,
                            segmentMillis,
                            Long::sum
                    );

            cursor = segmentEnd;
        }
    }

    /**
     * 全セルのミリ秒端数を決定的に配分し、
     * 日合計と時間・カテゴリー合計を一致させる。
     */
    private List<Map<UsageCategory, Long>>
            roundHourlyCategoryMillis(
                    List<Map<UsageCategory, Long>>
                            hourlyMillis,
                    long totalMillis
            ) {
        List<Map<UsageCategory, Long>> hourlySeconds =
                new ArrayList<>();

        List<RoundingCandidate> candidates =
                new ArrayList<>();

        long allocatedSeconds = 0;

        for (int hour = 0;
                hour < HOURS_PER_DAY;
                hour++) {
            Map<UsageCategory, Long> secondsMap =
                    new EnumMap<>(UsageCategory.class);

            for (Map.Entry<UsageCategory, Long> entry
                    : hourlyMillis.get(hour).entrySet()) {
                long millis = entry.getValue();

                if (millis <= 0) {
                    continue;
                }

                long seconds = millis / 1000;

                secondsMap.put(
                        entry.getKey(),
                        seconds
                );

                candidates.add(
                        new RoundingCandidate(
                                hour,
                                entry.getKey(),
                                millis % 1000
                        )
                );

                allocatedSeconds += seconds;
            }

            hourlySeconds.add(secondsMap);
        }

        candidates.sort(
                Comparator
                        .comparingLong(
                                RoundingCandidate::remainderMillis
                        )
                        .reversed()
                        .thenComparingInt(
                                RoundingCandidate::hour
                        )
                        .thenComparingInt(
                                candidate ->
                                        candidate
                                                .category()
                                                .ordinal()
                        )
        );

        long remainingSeconds =
                totalMillis / 1000
                - allocatedSeconds;

        if (remainingSeconds > candidates.size()) {
            throw new IllegalStateException(
                    "秒の端数を配分できません。"
            );
        }

        for (int index = 0;
                index < remainingSeconds;
                index++) {
            RoundingCandidate candidate =
                    candidates.get(index);

            hourlySeconds
                    .get(candidate.hour())
                    .merge(
                            candidate.category(),
                            1L,
                            Long::sum
                    );
        }

        return hourlySeconds;
    }

    private List<CategoryUsageResponse>
            createCategoryResponses(
                    Map<UsageCategory, Long> secondsMap
            ) {
        List<CategoryUsageResponse> responses =
                new ArrayList<>();

        /*
         * enumの定義順で返す。
         */
        for (UsageCategory category
                : UsageCategory.values()) {

            long seconds =
                    secondsMap.getOrDefault(
                            category,
                            0L
                    );

            if (seconds <= 0) {
                continue;
            }

            responses.add(
                    new CategoryUsageResponse(
                            category.key(),
                            category.label(),
                            seconds
                    )
            );
        }

        return responses;
    }
    
    /**
     * 選択日を含む日曜日～土曜日の利用分布を作る。
     */
    private WeeklyUsageResponse createWeeklyUsage(
            List<ActivityInterval> intervals,
            LocalDate weekStartDate,
            ZoneId zoneId,
            Instant now
    ) {
        String[] dayLabels = {
                "日",
                "月",
                "火",
                "水",
                "木",
                "金",
                "土"
        };

        List<WeeklyDayUsageResponse> days =
                new ArrayList<>();

        long weeklyTotalSeconds = 0;

        for (int dayIndex = 0;
                dayIndex < 7;
                dayIndex++) {

            LocalDate date =
                    weekStartDate.plusDays(dayIndex);

            ZonedDateTime dayStartZoned =
                    date.atStartOfDay(zoneId);

            ZonedDateTime fullDayEndZoned =
                    date
                            .plusDays(1)
                            .atStartOfDay(zoneId);

            Instant dayStart =
                    dayStartZoned.toInstant();

            /*
             * 過去日なら24時まで。
             * 今日なら現在時刻まで。
             * 未来日なら開始＝終了となり0秒。
             */
            Instant dayEnd =
                    determineEffectiveDayEnd(
                            dayStartZoned,
                            fullDayEndZoned,
                            now
                    );

            DayBreakdown breakdown =
                    createDayBreakdown(
                            intervals,
                            dayStart,
                            dayEnd,
                            zoneId
                    );

            weeklyTotalSeconds +=
                    breakdown.totalSeconds();

            days.add(
                    new WeeklyDayUsageResponse(
                            date,
                            dayLabels[dayIndex],
                            breakdown.totalSeconds(),
                            breakdown.categories()
                    )
            );
        }

        return new WeeklyUsageResponse(
                weekStartDate,
                weekStartDate.plusDays(6),
                weeklyTotalSeconds,
                days
        );
    }
    
    /**
     * 直近7日間と前7日間を比較し、
     * 何が増減の原因だったかを返す。
     */
    private ChangeDriversResponse createChangeDrivers(
            List<ActivityInterval> intervals,
            LocalDate targetDate,
            ZonedDateTime currentStart,
            ZonedDateTime currentEnd,
            ZonedDateTime previousStart,
            ZonedDateTime previousEnd
    ) {
        PeriodUsageBreakdown current =
                createPeriodUsageBreakdown(
                        intervals,
                        currentStart.toInstant(),
                        currentEnd.toInstant()
                );

        PeriodUsageBreakdown previous =
                createPeriodUsageBreakdown(
                        intervals,
                        previousStart.toInstant(),
                        previousEnd.toInstant()
                );

        /*
         * 前7日に記録がなければ、
         * 増減要因としては比較不能とする。
         */
        boolean available =
                previous.totalMillis() > 0;

        if (!available) {
            return new ChangeDriversResponse(
                    false,

                    targetDate.minusDays(6),
                    targetDate,

                    targetDate.minusDays(13),
                    targetDate.minusDays(7),

                    List.of(),
                    List.of()
            );
        }

        return new ChangeDriversResponse(
                true,

                targetDate.minusDays(6),
                targetDate,

                targetDate.minusDays(13),
                targetDate.minusDays(7),

                createCategoryChanges(
                        current.categoryMillis(),
                        previous.categoryMillis()
                ),

                createSiteChanges(
                        current.siteMillis(),
                        previous.siteMillis()
                )
        );
    }

    /**
     * 指定期間のカテゴリー別・サイト別使用時間を作る。
     */
    private PeriodUsageBreakdown createPeriodUsageBreakdown(
            List<ActivityInterval> intervals,
            Instant rangeStart,
            Instant rangeEnd
    ) {
        Map<UsageCategory, Long> categoryMillis =
                new EnumMap<>(UsageCategory.class);

        Map<String, Long> siteMillis =
                new HashMap<>();

        long totalMillis = 0;

        for (ActivityInterval interval : intervals) {
            Instant overlapStart = laterOf(
                    interval.startedAt(),
                    rangeStart
            );

            Instant overlapEnd = earlierOf(
                    interval.endedAt(),
                    rangeEnd
            );

            if (!overlapStart.isBefore(overlapEnd)) {
                continue;
            }

            long millis = Duration
                    .between(
                            overlapStart,
                            overlapEnd
                    )
                    .toMillis();

            if (millis <= 0) {
                continue;
            }

            totalMillis += millis;

            UsageCategory category =
                    categoryClassifier.classify(
                            interval.site()
                    );

            categoryMillis.merge(
                    category,
                    millis,
                    Long::sum
            );

            siteMillis.merge(
                    interval.site(),
                    millis,
                    Long::sum
            );
        }

        return new PeriodUsageBreakdown(
                totalMillis,
                categoryMillis,
                siteMillis
        );
    }

    /**
     * カテゴリー別の増減一覧を作る。
     */
    private List<UsageChangeResponse> createCategoryChanges(
            Map<UsageCategory, Long> currentMillis,
            Map<UsageCategory, Long> previousMillis
    ) {
        List<UsageChangeResponse> changes =
                new ArrayList<>();

        for (UsageCategory category
                : UsageCategory.values()) {

            long currentSeconds =
                    currentMillis.getOrDefault(
                            category,
                            0L
                    ) / 1000;

            long previousSeconds =
                    previousMillis.getOrDefault(
                            category,
                            0L
                    ) / 1000;

            long differenceSeconds =
                    currentSeconds - previousSeconds;

            if (differenceSeconds == 0) {
                continue;
            }

            changes.add(
                    new UsageChangeResponse(
                            category.key(),
                            category.label(),
                            currentSeconds,
                            previousSeconds,
                            differenceSeconds
                    )
            );
        }

        sortChanges(changes);

        return List.copyOf(changes);
    }

    /**
     * サイト別の増減一覧を作る。
     */
    private List<UsageChangeResponse> createSiteChanges(
            Map<String, Long> currentMillis,
            Map<String, Long> previousMillis
    ) {
        /*
         * 両期間に登場したすべてのサイトを対象にする。
         */
        TreeSet<String> sites = new TreeSet<>();

        sites.addAll(currentMillis.keySet());
        sites.addAll(previousMillis.keySet());

        List<UsageChangeResponse> changes =
                new ArrayList<>();

        for (String site : sites) {
            long currentSeconds =
                    currentMillis.getOrDefault(
                            site,
                            0L
                    ) / 1000;

            long previousSeconds =
                    previousMillis.getOrDefault(
                            site,
                            0L
                    ) / 1000;

            long differenceSeconds =
                    currentSeconds - previousSeconds;

            if (differenceSeconds == 0) {
                continue;
            }

            changes.add(
                    new UsageChangeResponse(
                            site,
                            site,
                            currentSeconds,
                            previousSeconds,
                            differenceSeconds
                    )
            );
        }

        sortChanges(changes);

        return List.copyOf(changes);
    }

    /**
     * 増減の絶対値が大きい順に並べる。
     */
    private void sortChanges(
            List<UsageChangeResponse> changes
    ) {
        changes.sort(
                Comparator
                        .comparingLong(
                                (
                                    UsageChangeResponse change
                                ) -> Math.abs(
                                        change.differenceSeconds()
                                )
                        )
                        .reversed()
                        .thenComparing(
                                UsageChangeResponse::label
                        )
        );
    }

    /**
     * ③直近7日 vs 前7日。
     */
    private PeriodComparisonResponse
            createSevenDayComparison(
                    List<ActivityInterval> intervals,
                    LocalDate targetDate,
                    ZonedDateTime currentStart,
                    ZonedDateTime currentEnd,
                    ZonedDateTime previousStart,
                    ZonedDateTime previousEnd
            ) {
        long currentSeconds = calculateTotalSeconds(
                intervals,
                currentStart.toInstant(),
                currentEnd.toInstant()
        );

        long previousSeconds = calculateTotalSeconds(
                intervals,
                previousStart.toInstant(),
                previousEnd.toInstant()
        );

        long differenceSeconds =
                currentSeconds - previousSeconds;

        Double differencePercent =
                calculateDifferencePercent(
                        differenceSeconds,
                        previousSeconds
                );

        return new PeriodComparisonResponse(
                targetDate.minusDays(6),
                targetDate,
                currentSeconds,

                targetDate.minusDays(13),
                targetDate.minusDays(7),
                previousSeconds,

                differenceSeconds,
                differencePercent
        );
    }

    /**
     * ④今日 vs 過去4回の同曜日中央値。
     */
    private SameWeekdayComparisonResponse
            createSameWeekdayComparison(
                    List<ActivityInterval> intervals,
                    LocalDate targetDate,
                    Instant targetStart,
                    Instant targetEnd,
                    ZoneId zoneId,
                    long targetSeconds
            ) {
        long elapsedMillis =
                Duration
                        .between(
                                targetStart,
                                targetEnd
                        )
                        .toMillis();

        Optional<Instant> firstStartedAt =
                activityLogRepository
                        .findFirstStartedAt();

        LocalDate firstRecordedDate =
                firstStartedAt
                        .map(
                                instant ->
                                        instant
                                                .atZone(zoneId)
                                                .toLocalDate()
                        )
                        .orElse(null);

        List<DayUsageResponse> samples =
                new ArrayList<>();

        for (int weeksAgo = 1;
                weeksAgo <= 4;
                weeksAgo++) {

            LocalDate sampleDate =
                    targetDate.minusWeeks(weeksAgo);

            /*
             * アプリ導入前の日は、
             * 「0秒」として比較対象に入れない。
             */
            if (
                firstRecordedDate != null
                && sampleDate.isBefore(
                        firstRecordedDate
                )
            ) {
                continue;
            }

            Instant sampleStart =
                    sampleDate
                            .atStartOfDay(zoneId)
                            .toInstant();

            Instant sampleEnd =
                    sampleStart.plusMillis(
                            elapsedMillis
                    );

            long sampleSeconds =
                    calculateTotalSeconds(
                            intervals,
                            sampleStart,
                            sampleEnd
                    );

            samples.add(
                    new DayUsageResponse(
                            sampleDate,
                            sampleSeconds
                    )
            );
        }

        Long medianSeconds =
                calculateMedian(samples);

        if (medianSeconds == null) {
            return new SameWeekdayComparisonResponse(
                    targetSeconds,
                    null,
                    null,
                    null,
                    samples
            );
        }

        long differenceSeconds =
                targetSeconds - medianSeconds;

        Double differencePercent =
                calculateDifferencePercent(
                        differenceSeconds,
                        medianSeconds
                );

        return new SameWeekdayComparisonResponse(
                targetSeconds,
                medianSeconds,
                differenceSeconds,
                differencePercent,
                samples
        );
    }

    private Long calculateMedian(
            List<DayUsageResponse> samples
    ) {
        if (samples.isEmpty()) {
            return null;
        }

        List<Long> sortedSeconds =
                samples
                        .stream()
                        .map(DayUsageResponse::seconds)
                        .sorted()
                        .toList();

        int size = sortedSeconds.size();
        int middle = size / 2;

        if (size % 2 == 1) {
            return sortedSeconds.get(middle);
        }

        long lower =
                sortedSeconds.get(middle - 1);

        long upper =
                sortedSeconds.get(middle);

        return (lower + upper) / 2;
    }

    private long calculateTotalSeconds(
            List<ActivityInterval> intervals,
            Instant rangeStart,
            Instant rangeEnd
    ) {
        long totalMillis = 0;

        for (ActivityInterval interval : intervals) {
            Instant overlapStart = laterOf(
                    interval.startedAt(),
                    rangeStart
            );

            Instant overlapEnd = earlierOf(
                    interval.endedAt(),
                    rangeEnd
            );

            if (!overlapStart.isBefore(overlapEnd)) {
                continue;
            }

            totalMillis += Duration
                    .between(
                            overlapStart,
                            overlapEnd
                    )
                    .toMillis();
        }

        return totalMillis / 1000;
    }

    private Double calculateDifferencePercent(
            long differenceSeconds,
            long comparisonSeconds
    ) {
        if (comparisonSeconds <= 0) {
            return null;
        }

        return differenceSeconds
                * 100.0
                / comparisonSeconds;
    }

    private Instant earlierOf(
            Instant first,
            Instant second
    ) {
        return first.isBefore(second)
                ? first
                : second;
    }

    private Instant laterOf(
            Instant first,
            Instant second
    ) {
        return first.isAfter(second)
                ? first
                : second;
    }

    private record DayBreakdown(
            long totalSeconds,
            List<CategoryUsageResponse> categories,
            List<HourlyUsageResponse> hourly
    ) {
    }
    /**
     * 期間内の集計途中データ。
     */
    private record PeriodUsageBreakdown(
            long totalMillis,
            Map<UsageCategory, Long> categoryMillis,
            Map<String, Long> siteMillis
    ) {
    }

    private record RoundingCandidate(
            int hour,
            UsageCategory category,
            long remainderMillis
    ) {
    }
}
