const SUMMARY_API = "/api/summary";
const ANALYTICS_API = "/api/analytics";

const CATEGORY_META = Object.freeze({
    ai: {
        label: "AI",
        className: "category-ai"
    },

    development: {
        label: "開発・学習",
        className: "category-development"
    },

    video: {
        label: "動画・音楽",
        className: "category-video"
    },

    search: {
        label: "検索・情報収集",
        className: "category-search"
    },

    sns: {
        label: "SNS",
        className: "category-sns"
    },

    other: {
        label: "その他",
        className: "category-other"
    }
});

const dateInput =
    document.getElementById("dateInput");

const previousDateButton =
    document.getElementById("previousDateButton");

const nextDateButton =
    document.getElementById("nextDateButton");

const refreshButton =
    document.getElementById("refreshButton");

const totalTime =
    document.getElementById("totalTime");

const selectedDateText =
    document.getElementById("selectedDateText");

const sameWeekdayDifference =
    document.getElementById(
        "sameWeekdayDifference"
    );

const sameWeekdayDetail =
    document.getElementById(
        "sameWeekdayDetail"
    );

const sevenDayDifference =
    document.getElementById(
        "sevenDayDifference"
    );

const sevenDayDetail =
    document.getElementById(
        "sevenDayDetail"
    );

const categoryCount =
    document.getElementById("categoryCount");

const categoryList =
    document.getElementById("categoryList");

const categoryEmptyMessage =
    document.getElementById(
        "categoryEmptyMessage"
    );

const hourlyChart =
    document.getElementById("hourlyChart");

const hourlyDetail =
    document.getElementById("hourlyDetail");

const siteCount =
    document.getElementById("siteCount");

const siteList =
    document.getElementById("siteList");

const emptyMessage =
    document.getElementById("emptyMessage");

const statusMessage =
    document.getElementById("statusMessage");
	const weeklyRange =
	    document.getElementById("weeklyRange");

	const weeklyChart =
	    document.getElementById("weeklyChart");

	const weeklyDetail =
	    document.getElementById("weeklyDetail");
		const changeDriversRange =
		    document.getElementById(
		        "changeDriversRange"
		    );

		const changeDriversUnavailable =
		    document.getElementById(
		        "changeDriversUnavailable"
		    );

		const changeDriversContent =
		    document.getElementById(
		        "changeDriversContent"
		    );

		const categoryChangeList =
		    document.getElementById(
		        "categoryChangeList"
		    );

		const siteChangeList =
		    document.getElementById(
		        "siteChangeList"
		    );

/**
 * Dateをローカル日付のyyyy-MM-ddへ変換する。
 */
function formatDateForInput(date) {
    const year = date.getFullYear();

    const month = String(
        date.getMonth() + 1
    ).padStart(2, "0");

    const day = String(
        date.getDate()
    ).padStart(2, "0");

    return `${year}-${month}-${day}`;
}

/**
 * yyyy-MM-ddをローカル時刻のDateへ変換する。
 */
function parseInputDate(value) {
    const [year, month, day] =
        value.split("-").map(Number);

    return new Date(
        year,
        month - 1,
        day
    );
}

function formatDisplayDate(value) {
    const date = parseInputDate(value);

    return new Intl.DateTimeFormat(
        "ja-JP",
        {
            year: "numeric",
            month: "long",
            day: "numeric",
            weekday: "short"
        }
    ).format(date);
}

function formatShortDate(value) {
    if (!value) {
        return "-";
    }

    const [, month, day] =
        value.split("-").map(Number);

    return `${month}/${day}`;
}

function formatDateRange(startDate, endDate) {
    return `${formatShortDate(startDate)}〜${
        formatShortDate(endDate)
    }`;
}

/**
 * 秒数を詳細表示へ変換する。
 */
function formatDuration(totalSeconds) {
    const safeSeconds = Math.max(
        0,
        Math.floor(
            Number(totalSeconds) || 0
        )
    );

    const hours =
        Math.floor(safeSeconds / 3600);

    const minutes =
        Math.floor(
            (safeSeconds % 3600) / 60
        );

    const seconds =
        safeSeconds % 60;

    if (hours > 0) {
        return `${hours}時間 ${minutes}分 ${seconds}秒`;
    }

    if (minutes > 0) {
        return `${minutes}分 ${seconds}秒`;
    }

    return `${seconds}秒`;
}

/**
 * 比較カード用の短い時間表示。
 */
function formatCompactDuration(totalSeconds) {
    const safeSeconds = Math.max(
        0,
        Math.floor(
            Number(totalSeconds) || 0
        )
    );

    const hours =
        Math.floor(safeSeconds / 3600);

    const minutes =
        Math.floor(
            (safeSeconds % 3600) / 60
        );

    const seconds =
        safeSeconds % 60;

    if (hours > 0) {
        return `${hours}時間${minutes}分`;
    }

    if (minutes > 0) {
        return `${minutes}分`;
    }

    return `${seconds}秒`;
}

function formatSignedDuration(seconds) {
    const numericSeconds =
        Number(seconds) || 0;

    const sign =
        numericSeconds > 0
            ? "+"
            : numericSeconds < 0
                ? "-"
                : "±";

    return `${sign}${
        formatCompactDuration(
            Math.abs(numericSeconds)
        )
    }`;
}

function formatSignedPercent(percent) {
    const numericPercent =
        Number(percent);

    if (!Number.isFinite(numericPercent)) {
        return "";
    }

    const sign =
        numericPercent > 0
            ? "+"
            : numericPercent < 0
                ? ""
                : "±";

    return `${sign}${
        numericPercent.toFixed(1)
    }%`;
}

function getCategoryMeta(
    category,
    fallbackLabel
) {
    return CATEGORY_META[category] ?? {
        label: fallbackLabel || category || "その他",
        className: "category-other"
    };
}

function setComparisonStyle(
    element,
    difference
) {
    element.classList.remove(
        "increase",
        "decrease",
        "same",
        "unavailable"
    );

    if (difference === null
        || difference === undefined) {

        element.classList.add("unavailable");
        return;
    }

    if (difference > 0) {
        element.classList.add("increase");
        return;
    }

    if (difference < 0) {
        element.classList.add("decrease");
        return;
    }

    element.classList.add("same");
}

/* ========================================
   比較カード
   ======================================== */

function renderSevenDayComparison(comparison) {
    if (!comparison) {
        sevenDayDifference.textContent =
            "比較データなし";

        sevenDayDetail.textContent =
            "比較情報を取得できませんでした";

        setComparisonStyle(
            sevenDayDifference,
            null
        );

        return;
    }

    const currentRange =
        formatDateRange(
            comparison.currentStartDate,
            comparison.currentEndDate
        );

    const previousRange =
        formatDateRange(
            comparison.previousStartDate,
            comparison.previousEndDate
        );

    sevenDayDetail.textContent =
        `${currentRange} ${
            formatCompactDuration(
                comparison.currentSeconds
            )
        } / ${previousRange} ${
            formatCompactDuration(
                comparison.previousSeconds
            )
        }`;

    if (
        comparison.previousSeconds <= 0
        || comparison.differencePercent === null
    ) {
        sevenDayDifference.textContent =
            "比較データなし";

        setComparisonStyle(
            sevenDayDifference,
            null
        );

        return;
    }

    sevenDayDifference.textContent =
        `${formatSignedDuration(
            comparison.differenceSeconds
        )}（${formatSignedPercent(
            comparison.differencePercent
        )}）`;

    setComparisonStyle(
        sevenDayDifference,
        comparison.differenceSeconds
    );
}

function renderSameWeekdayComparison(comparison) {
    if (
        !comparison
        || comparison.medianSeconds === null
    ) {
        sameWeekdayDifference.textContent =
            "比較データなし";

        const sampleCount =
            Array.isArray(comparison?.samples)
                ? comparison.samples.length
                : 0;

        sameWeekdayDetail.textContent =
            sampleCount === 0
                ? "履歴が1週間以上たまると自動表示されます"
                : `比較可能な同曜日は${sampleCount}日です`;

        setComparisonStyle(
            sameWeekdayDifference,
            null
        );

        return;
    }

    sameWeekdayDifference.textContent =
        `${formatSignedDuration(
            comparison.differenceSeconds
        )}（${formatSignedPercent(
            comparison.differencePercent
        )}）`;

    const sampleCount =
        comparison.samples.length;

    sameWeekdayDetail.textContent =
        `過去${sampleCount}回の同曜日中央値：${
            formatCompactDuration(
                comparison.medianSeconds
            )
        }`;

    setComparisonStyle(
        sameWeekdayDifference,
        comparison.differenceSeconds
    );
}

/* ========================================
   増減要因
   ======================================== */

function createChangeItem(
    change,
    displayName
) {
    const item =
        document.createElement("article");

    item.className = "change-item";

    const name =
        document.createElement("span");

    name.className = "change-name";
    name.textContent = displayName;

    const difference =
        document.createElement("span");

    difference.className =
        "change-difference";

    if (change.differenceSeconds > 0) {
        difference.classList.add(
            "increase"
        );
    } else {
        difference.classList.add(
            "decrease"
        );
    }

    difference.textContent =
        formatSignedDuration(
            change.differenceSeconds
        );

    const detail =
        document.createElement("span");

    detail.className = "change-detail";

    detail.textContent =
        `前 ${
            formatCompactDuration(
                change.previousSeconds
            )
        } → 今 ${
            formatCompactDuration(
                change.currentSeconds
            )
        }`;

    item.append(
        name,
        difference,
        detail
    );

    return item;
}

function renderNoChangeMessage(container) {
    const message =
        document.createElement("p");

    message.className = "change-none";
    message.textContent =
        "目立った増減はありません。";

    container.append(message);
}

function renderChangeDrivers(changeDrivers) {
    categoryChangeList.replaceChildren();
    siteChangeList.replaceChildren();

    if (!changeDrivers) {
        changeDriversContent.hidden = true;
        changeDriversUnavailable.hidden = false;

        changeDriversUnavailable.textContent =
            "増減情報を取得できませんでした。";

        return;
    }

    changeDriversRange.textContent =
        `${formatDateRange(
            changeDrivers.currentStartDate,
            changeDrivers.currentEndDate
        )} と ${
            formatDateRange(
                changeDrivers.previousStartDate,
                changeDrivers.previousEndDate
            )
        }を比較`;

    if (!changeDrivers.available) {
        changeDriversContent.hidden = true;
        changeDriversUnavailable.hidden = false;

        changeDriversUnavailable.textContent =
            "前7日の記録がないため、"
            + "まだ増減要因を比較できません🙂";

        return;
    }

    changeDriversUnavailable.hidden = true;
    changeDriversContent.hidden = false;

    const categoryChanges =
        Array.isArray(
            changeDrivers.categoryChanges
        )
            ? changeDrivers.categoryChanges
            : [];

    const siteChanges =
        Array.isArray(
            changeDrivers.siteChanges
        )
            ? changeDrivers.siteChanges
            : [];

    /*
     * カテゴリーは最大6種類なので全部表示。
     */
    for (const change of categoryChanges) {
        const meta =
            getCategoryMeta(
                change.key,
                change.label
            );

        categoryChangeList.append(
            createChangeItem(
                change,
                meta.label
            )
        );
    }

    /*
     * サイトは増減の大きい上位8件。
     */
    for (
        const change
        of siteChanges.slice(0, 8)
    ) {
        siteChangeList.append(
            createChangeItem(
                change,
                getSiteDisplayName(
                    change.key
                )
            )
        );
    }

    if (categoryChanges.length === 0) {
        renderNoChangeMessage(
            categoryChangeList
        );
    }

    if (siteChanges.length === 0) {
        renderNoChangeMessage(
            siteChangeList
        );
    }
}

/* ========================================
   カテゴリー
   ======================================== */

function createCategoryRow(
    categoryUsage,
    totalSeconds
) {
    const meta =
        getCategoryMeta(
            categoryUsage.category,
            categoryUsage.label
        );

    const share =
        totalSeconds > 0
            ? (
                categoryUsage.seconds
                / totalSeconds
            ) * 100
            : 0;

    const row =
        document.createElement("article");

    row.className = "category-row";

    const header =
        document.createElement("div");

    header.className =
        "category-row-header";

    const nameArea =
        document.createElement("div");

    nameArea.className =
        "category-name-area";

    const dot =
        document.createElement("span");

    dot.className =
        `category-dot ${meta.className}`;

    const name =
        document.createElement("span");

    name.className = "category-name";
    name.textContent = meta.label;

    const shareText =
        document.createElement("span");

    shareText.className = "category-share";
    shareText.textContent =
        `${share.toFixed(1)}%`;

    name.append(shareText);
    nameArea.append(dot, name);

    const duration =
        document.createElement("span");

    duration.className =
        "category-duration";

    duration.textContent =
        formatDuration(
            categoryUsage.seconds
        );

    header.append(nameArea, duration);

    const track =
        document.createElement("div");

    track.className = "category-track";

    const fill =
        document.createElement("div");

    fill.className =
        `category-fill ${meta.className}`;

    fill.style.setProperty(
        "--category-width",
        `${Math.min(share, 100)}%`
    );

    track.append(fill);
    row.append(header, track);

    return row;
}

function renderCategories(analytics) {
    categoryList.replaceChildren();

    const categories =
        Array.isArray(analytics.categories)
            ? analytics.categories
            : [];

    categoryCount.textContent =
        `${categories.length}カテゴリー`;

    categoryEmptyMessage.hidden =
        categories.length !== 0;

    for (const category of categories) {
        categoryList.append(
            createCategoryRow(
                category,
                analytics.totalSeconds
            )
        );
    }
}

/* ========================================
   24時間グラフ
   ======================================== */

function renderHourlyDetail(hourData) {
    if (!hourData || hourData.totalSeconds <= 0) {
        hourlyDetail.textContent =
            `${hourData?.hour ?? 0}時台は記録がありません。`;

        return;
    }

    const categoryDetails =
        hourData.categories
            .filter(category => category.seconds > 0)
            .map(category => {
                const meta =
                    getCategoryMeta(
                        category.category,
                        category.label
                    );

                return `${meta.label} ${
                    formatDuration(
                        category.seconds
                    )
                }`;
            })
            .join(" / ");

    const nextHour =
        (hourData.hour + 1) % 24;

    hourlyDetail.textContent =
        `${String(hourData.hour).padStart(2, "0")}:00〜${
            String(nextHour).padStart(2, "0")
        }:00　合計 ${
            formatDuration(
                hourData.totalSeconds
            )
        }　${categoryDetails}`;
}

function selectHour(
    button,
    hourData
) {
    for (
        const currentButton
        of hourlyChart.querySelectorAll(
            ".hour-column"
        )
    ) {
        currentButton.classList.remove(
            "selected"
        );
    }

    button.classList.add("selected");
    renderHourlyDetail(hourData);
}

function renderHourlyChart(hourlyUsage) {
    hourlyChart.replaceChildren();

    const hourlyMap =
        new Map(
            (hourlyUsage ?? []).map(
                item => [item.hour, item]
            )
        );

    let busiestButton = null;
    let busiestHourData = null;

    for (let hour = 0; hour < 24; hour++) {
        const hourData =
            hourlyMap.get(hour) ?? {
                hour: hour,
                totalSeconds: 0,
                categories: []
            };

        const button =
            document.createElement("button");

        button.type = "button";
        button.className = "hour-column";

        button.setAttribute(
            "aria-label",
            `${hour}時台 ${
                formatDuration(
                    hourData.totalSeconds
                )
            }`
        );

        const stack =
            document.createElement("div");

        stack.className = "hour-stack";

        for (
            const category
            of hourData.categories
        ) {
            if (category.seconds <= 0) {
                continue;
            }

            const meta =
                getCategoryMeta(
                    category.category,
                    category.label
                );

            const segment =
                document.createElement("div");

            segment.className =
                `hour-segment ${meta.className}`;

            const height =
                (
                    category.seconds
                    / 3600
                ) * 100;

            segment.style.setProperty(
                "--segment-height",
                `${Math.min(height, 100)}%`
            );

            segment.title =
                `${hour}時台・${meta.label}：${
                    formatDuration(
                        category.seconds
                    )
                }`;

            stack.append(segment);
        }

        const label =
            document.createElement("span");

        label.className = "hour-label";

        label.textContent =
            hour % 3 === 0 || hour === 23
                ? String(hour)
                : "";

        button.append(stack, label);

        button.addEventListener(
            "click",
            () => {
                selectHour(
                    button,
                    hourData
                );
            }
        );

        hourlyChart.append(button);

        if (
            busiestHourData === null
            || hourData.totalSeconds
                > busiestHourData.totalSeconds
        ) {
            busiestHourData = hourData;
            busiestButton = button;
        }
    }

    if (
        busiestButton
        && busiestHourData
        && busiestHourData.totalSeconds > 0
    ) {
        selectHour(
            busiestButton,
            busiestHourData
        );
    } else {
        hourlyDetail.textContent =
            "この日の時間帯別記録はありません。";
    }
}

/* ========================================
   1週間の利用分布
   ======================================== */

function renderWeeklyDetail(dayData) {
    if (!dayData || dayData.totalSeconds <= 0) {
        weeklyDetail.textContent =
            `${formatShortDate(
                dayData?.date
            )}（${dayData?.dayLabel ?? "-"}）は`
            + "記録がありません。";

        return;
    }

    const categoryDetails =
        dayData.categories
            .filter(
                category =>
                    category.seconds > 0
            )
            .map(category => {
                const meta =
                    getCategoryMeta(
                        category.category,
                        category.label
                    );

                return `${meta.label} ${
                    formatDuration(
                        category.seconds
                    )
                }`;
            })
            .join(" / ");

    weeklyDetail.textContent =
        `${formatShortDate(dayData.date)}`
        + `（${dayData.dayLabel}）`
        + `　合計 ${
            formatDuration(
                dayData.totalSeconds
            )
        }`
        + (
            categoryDetails
                ? `　${categoryDetails}`
                : ""
        );
}

function selectWeeklyDay(
    button,
    dayData
) {
    for (
        const currentButton
        of weeklyChart.querySelectorAll(
            ".week-day-column"
        )
    ) {
        currentButton.classList.remove(
            "selected"
        );
    }

    button.classList.add("selected");
    renderWeeklyDetail(dayData);
}

function renderWeeklyChart(
    weekly,
    selectedDate
) {
    weeklyChart.replaceChildren();

    const days =
        Array.isArray(weekly?.days)
            ? weekly.days
            : [];

    if (days.length === 0) {
        weeklyRange.textContent =
            "週間データなし";

        weeklyDetail.textContent =
            "週間データを取得できませんでした。";

        return;
    }

    weeklyRange.textContent =
        `${formatShortDate(
            weekly.startDate
        )}〜${formatShortDate(
            weekly.endDate
        )}`
        + `　週間合計 ${
            formatDuration(
                weekly.totalSeconds
            )
        }`;

    /*
     * その週で最も長い日をグラフの最大高さにする。
     * 日ごとの差が見えやすくなる。
     */
    const maximumSeconds =
        Math.max(
            ...days.map(
                day => day.totalSeconds
            ),
            1
        );

    let selectedButton = null;
    let selectedDayData = null;

    let busiestButton = null;
    let busiestDayData = null;

    for (const dayData of days) {
        const button =
            document.createElement("button");

        button.type = "button";
        button.className =
            "week-day-column";

        button.setAttribute(
            "aria-label",
            `${dayData.dayLabel}曜日 ${
                formatDuration(
                    dayData.totalSeconds
                )
            }`
        );

        const totalLabel =
            document.createElement("span");

        totalLabel.className =
            "week-total-label";

        totalLabel.textContent =
            dayData.totalSeconds > 0
                ? formatCompactDuration(
                    dayData.totalSeconds
                )
                : "-";

        const bar =
            document.createElement("div");

        bar.className = "week-bar";

        for (
            const category
            of dayData.categories
        ) {
            if (category.seconds <= 0) {
                continue;
            }

            const meta =
                getCategoryMeta(
                    category.category,
                    category.label
                );

            const segment =
                document.createElement("div");

            segment.className =
                `week-segment ${meta.className}`;

            /*
             * そのカテゴリー秒数を、
             * 週で最も長い日の合計秒数と比較する。
             */
            const height =
                (
                    category.seconds
                    / maximumSeconds
                ) * 100;

            segment.style.setProperty(
                "--week-segment-height",
                `${Math.min(height, 100)}%`
            );

            segment.title =
                `${dayData.dayLabel}曜日・`
                + `${meta.label}：`
                + formatDuration(
                    category.seconds
                );

            bar.append(segment);
        }

        const dayLabel =
            document.createElement("span");

        dayLabel.className =
            "week-day-label";

        dayLabel.textContent =
            dayData.dayLabel;

        const dateLabel =
            document.createElement("span");

        dateLabel.className =
            "week-date-label";

        dateLabel.textContent =
            formatShortDate(dayData.date);

        button.append(
            totalLabel,
            bar,
            dayLabel,
            dateLabel
        );

        button.addEventListener(
            "click",
            () => {
                selectWeeklyDay(
                    button,
                    dayData
                );
            }
        );

        weeklyChart.append(button);

        /*
         * 初期選択は現在画面で選択している日。
         */
        if (dayData.date === selectedDate) {
            selectedButton = button;
            selectedDayData = dayData;
        }

        if (
            busiestDayData === null
            || dayData.totalSeconds
                > busiestDayData.totalSeconds
        ) {
            busiestButton = button;
            busiestDayData = dayData;
        }
    }

    /*
     * 選択日が週内にあればその日。
     * なければ最も利用時間が長い日を選ぶ。
     */
    if (selectedButton && selectedDayData) {
        selectWeeklyDay(
            selectedButton,
            selectedDayData
        );
    } else if (
        busiestButton
        && busiestDayData
    ) {
        selectWeeklyDay(
            busiestButton,
            busiestDayData
        );
    }
}

/* ========================================
   サイト別
   ======================================== */

function getSiteDisplayName(site) {
    const displayNames = {
        "chatgpt.com": "ChatGPT",
        "claude.ai": "Claude",
        "gemini.google.com": "Gemini",
        "youtube.com": "YouTube",
        "github.com": "GitHub",
        "docs.aws.amazon.com": "AWS Docs",
        "aws.amazon.com": "AWS",
        "start.spring.io": "Spring Initializr",
        "adoptium.net": "Eclipse Adoptium"
    };

    return displayNames[site] ?? site;
}

function createSiteRow(
    siteUsage,
    maximumSeconds
) {
    const row =
        document.createElement("article");

    row.className = "site-row";

    const header =
        document.createElement("div");

    header.className =
        "site-row-header";

    const nameBlock =
        document.createElement("div");

    nameBlock.className =
        "site-name-block";

    const name =
        document.createElement("span");

    name.className = "site-name";
    name.textContent =
        getSiteDisplayName(
            siteUsage.site
        );

    const domain =
        document.createElement("span");

    domain.className = "site-domain";
    domain.textContent = siteUsage.site;

    const duration =
        document.createElement("span");

    duration.className = "site-duration";
    duration.textContent =
        formatDuration(
            siteUsage.seconds
        );

    nameBlock.append(name, domain);
    header.append(nameBlock, duration);

    const barBackground =
        document.createElement("div");

    barBackground.className =
        "usage-bar-background";

    const bar =
        document.createElement("div");

    bar.className = "usage-bar";

    const width =
        maximumSeconds > 0
            ? (
                siteUsage.seconds
                / maximumSeconds
            ) * 100
            : 0;

    bar.style.setProperty(
        "--usage-width",
        `${Math.min(width, 100)}%`
    );

    barBackground.append(bar);
    row.append(header, barBackground);

    return row;
}

function renderSiteSummary(summary) {
    siteList.replaceChildren();

    const sites =
        Array.isArray(summary.sites)
            ? summary.sites
            : [];

    siteCount.textContent =
        `${sites.length}サイト`;

    emptyMessage.hidden =
        sites.length !== 0;

    const maximumSeconds =
        sites.length > 0
            ? Math.max(
                ...sites.map(
                    site => site.seconds
                )
            )
            : 0;

    for (const siteUsage of sites) {
        siteList.append(
            createSiteRow(
                siteUsage,
                maximumSeconds
            )
        );
    }
}

/* ========================================
   API取得
   ======================================== */

async function fetchJson(url) {
    const response = await fetch(
        url,
        {
            cache: "no-store"
        }
    );

    if (!response.ok) {
        const responseText =
            await response.text();

        throw new Error(
            `HTTP ${response.status}: ${
                responseText
            }`
        );
    }

    return response.json();
}

function setLoading(isLoading) {
    refreshButton.disabled = isLoading;
    previousDateButton.disabled = isLoading;
    nextDateButton.disabled = isLoading;
}

async function loadDashboard() {
    const targetDate = dateInput.value;

    if (!targetDate) {
        return;
    }

    setLoading(true);

    statusMessage.classList.remove("error");
    statusMessage.textContent =
        "読み込み中…";

    try {
        const encodedDate =
            encodeURIComponent(targetDate);

        const [
            analytics,
            summary
        ] = await Promise.all([
            fetchJson(
                `${ANALYTICS_API}?date=${
                    encodedDate
                }`
            ),

            fetchJson(
                `${SUMMARY_API}?date=${
                    encodedDate
                }`
            )
        ]);

        totalTime.textContent =
            formatDuration(
                analytics.totalSeconds
            );

        selectedDateText.textContent =
            formatDisplayDate(
                analytics.date
            );

			renderSevenDayComparison(
			    analytics.sevenDayComparison
			);

			renderChangeDrivers(
			    analytics.changeDrivers
			);

			renderSameWeekdayComparison(
			    analytics.sameWeekdayComparison
			);

        renderCategories(analytics);

		renderHourlyChart(
		    analytics.hourly
		);

		renderWeeklyChart(
		    analytics.weekly,
		    analytics.date
		);

		renderSiteSummary(summary);

        statusMessage.textContent =
            `更新 ${
                new Date().toLocaleTimeString(
                    "ja-JP"
                )
            }`;
    } catch (error) {
        console.error(
            "[Chrome Time Tracker] "
            + "画面取得失敗",
            error
        );

        statusMessage.classList.add("error");
        statusMessage.textContent =
            "集計を取得できませんでした😣";
    } finally {
        setLoading(false);
    }
}

function moveDate(days) {
    const currentDate =
        parseInputDate(dateInput.value);

    currentDate.setDate(
        currentDate.getDate() + days
    );

    const nextValue =
        formatDateForInput(currentDate);

    /*
     * 未来日は表示しない。
     */
    if (nextValue > dateInput.max) {
        return;
    }

    dateInput.value = nextValue;
    void loadDashboard();
}

previousDateButton.addEventListener(
    "click",
    () => {
        moveDate(-1);
    }
);

nextDateButton.addEventListener(
    "click",
    () => {
        moveDate(1);
    }
);

refreshButton.addEventListener(
    "click",
    () => {
        void loadDashboard();
    }
);

dateInput.addEventListener(
    "change",
    () => {
        void loadDashboard();
    }
);

const today =
    formatDateForInput(new Date());

dateInput.max = today;
dateInput.value = today;

void loadDashboard();