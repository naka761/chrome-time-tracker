const API_URL = "http://127.0.0.1:18080/api/context";


/**
 * タブのURLからhostnameだけを取得する。
 *
 * HTTP/HTTPS以外と、このTracker自身の画面はnullにする。
 */
function extractSite(url) {
    if (!url) {
        return null;
    }

    try {
        const parsedUrl = new URL(url);

        if (
            parsedUrl.protocol !== "http:"
            && parsedUrl.protocol !== "https:"
        ) {
            return null;
        }

        /*
         * Chrome Time Tracker自身は計測しない。
         */
        const isTrackerPage =
            (
                parsedUrl.hostname === "127.0.0.1"
                || parsedUrl.hostname === "localhost"
            )
            && parsedUrl.port === "18080";

        if (isTrackerPage) {
            return null;
        }

        return parsedUrl.hostname || null;
    } catch (error) {
        console.warn(
            "[Chrome Time Tracker] URLを解析できませんでした。",
            url,
            error
        );

        return null;
    }
}

/**
 * 現在のサイト情報をJavaへ送信する。
 */
async function postContext(site) {
    const observedAt = Date.now();

    const response = await fetch(API_URL, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            site: site,
            observedAt: observedAt
        })
    });

    if (!response.ok) {
        const responseBody = await response.text();

        throw new Error(
            `Java APIエラー: ${response.status} ${responseBody}`
        );
    }

    console.log(
        "[Chrome Time Tracker] 送信成功:",
        site ?? "(計測対象なし)",
        new Date(observedAt).toLocaleString()
    );
}

/**
 * 現在選択されているChromeタブを調べ、
 * hostnameをJavaへ送る。
 */
async function syncCurrentTab() {
    try {
        /*
         * 通常のChromeウィンドウが一つもない場合、
         * 現在の計測を終了する。
         */
        const normalWindows = await chrome.windows.getAll({
            windowTypes: ["normal"]
        });

        if (normalWindows.length === 0) {
            await postContext(null);
            return;
        }

        /*
         * 最後に選択されたChromeウィンドウの
         * アクティブタブを取得する。
         */
        const tabs = await chrome.tabs.query({
            active: true,
            lastFocusedWindow: true
        });

        const currentTab = tabs[0];
        const site = extractSite(currentTab?.url);

        await postContext(site);
    } catch (error) {
        /*
         * Spring Bootが起動していない場合など。
         * 拡張機能自体は停止させない。
         */
        console.warn(
            "[Chrome Time Tracker] 現在サイトを送信できませんでした。",
            error
        );
    }
}

/**
 * タブを閉じた直後は、次のタブがまだ選択されていない
 * 可能性があるため、次の処理タイミングで再取得する。
 */
function scheduleSync() {
    setTimeout(() => {
        void syncCurrentTab();
    }, 0);
}

/*
 * 拡張機能をインストール・再読込したとき。
 */
chrome.runtime.onInstalled.addListener(() => {
    void syncCurrentTab();
});

/*
 * Chromeを起動したとき。
 */
chrome.runtime.onStartup.addListener(() => {
    void syncCurrentTab();
});

/*
 * 別のタブへ切り替えたとき。
 */
chrome.tabs.onActivated.addListener(() => {
    void syncCurrentTab();
});

/*
 * 同じタブ内でURLが変わったとき。
 *
 * 読込状態やタイトル変更では送らず、
 * URL変更時だけ送る。
 */
chrome.tabs.onUpdated.addListener(
    (_tabId, changeInfo, tab) => {
        if (
            tab.active
            && typeof changeInfo.url === "string"
        ) {
            void syncCurrentTab();
        }
    }
);

/*
 * タブを閉じたとき。
 *
 * Chromeウィンドウ全体を閉じている途中なら、
 * 各タブでは処理せずwindows.onRemovedに任せる。
 */
chrome.tabs.onRemoved.addListener(
    (_tabId, removeInfo) => {
        if (!removeInfo.isWindowClosing) {
            scheduleSync();
        }
    }
);

/*
 * 別のChromeウィンドウへ移動したとき。
 *
 * ChromeからEclipseなどへ移った場合は
 * WINDOW_ID_NONEになるが、今回は計測を止めない。
 */
chrome.windows.onFocusChanged.addListener(
    (windowId) => {
        if (windowId !== chrome.windows.WINDOW_ID_NONE) {
            void syncCurrentTab();
        }
    }
);

/*
 * Chromeウィンドウを閉じたとき。
 *
 * 別ウィンドウが残っていればそのタブへ切替。
 * 一つも残っていなければsite=nullを送る。
 */
chrome.windows.onRemoved.addListener(() => {
    scheduleSync();
});