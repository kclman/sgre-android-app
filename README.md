# SGRE Android WebView App

這是一個簡單的 Android APK 專案，用 WebView 包裝本地 ESP 網頁，避免使用 Chrome 網址列。

## 功能

- 第一次開啟可設定 ESP 網址
- 網址會儲存在手機本機
- 支援本地 HTTP，例如 `http://192.168.31.201:81`
- 支援 `.local`，例如 `http://sgre.local:81`
- App 內沒有 Chrome 網址列
- 提供「刷新」、「首頁」、「設定」按鈕
- 可用 GitHub Actions 自動編譯 APK，不需要安裝 Android Studio

## GitHub Actions 編譯 APK

1. 建立一個新的 GitHub Repository，例如 `sgre-android-app`
2. 把本專案所有檔案上傳到 Repo 根目錄
3. 到 GitHub Repo 的 `Actions`
4. 點選 `Build SGRE Android APK`
5. 按 `Run workflow`
6. 等待完成後，在該次 workflow run 底部下載 artifact：`SGRE-debug-apk`
7. 解壓縮後得到 `app-debug.apk`
8. 傳到 Android 手機安裝

## 手機安裝提醒

這是 Debug APK，Android 會提示「未知來源」或「Play Protect」。
允許安裝後即可使用。

## 預設網址

預設網址在 `MainActivity.java`：

```java
private static final String DEFAULT_URL = "http://192.168.31.201:81";
```

使用者也可以在 App 內按「設定」修改網址，不一定要重新編譯。

## 後續可擴充

- 多設備清單
- 自訂 App icon
- 背景警報通知
- 自動掃描區網設備


## V2 修改

- 更換 SGRE App icon。
- 修正 Android 狀態列與 App 上方工具列重疊問題。
- 上方工具列會自動避開手機系統狀態列高度。


## V3 修改

- 改為無原生上方工具列，全畫面顯示 ESP 原本手機版 UI。
- 修正 WebView 與 Chrome 顯示比例不同的問題：
  - `textZoom = 100`
  - 關閉 `loadWithOverviewMode`
  - 關閉 `useWideViewPort`
- 保留本地 HTTP 支援。
- 保留網址可設定功能。
- 重新設定網址方式：長按 App 畫面。


## V4 修改

- App icon 改成使用你指定的綠色 SGRE Logo。
- App 原生外殼改成配合 Android 系統自動切換淺色 / 深色模式。
- 狀態列與底部導覽列顏色會跟隨系統模式切換。
- 保留長按畫面修改 ESP 網址的功能。

> 注意：目前自動切換的是 App 原生外殼與系統列；ESP 網頁本身仍以它自己的頁面樣式為主。


## V4 Fixed 修改

- 修正 GitHub Actions 編譯錯誤：
  - 移除不存在的 `android:style/Theme.DeviceDefault.DayNight.NoActionBar`
  - 改用 `Theme.Material.Light.NoActionBar` + `values-night/Theme.Material.NoActionBar`
- 保留系統淺色 / 深色模式自動切換。
- 保留中間綠色 SGRE Logo App icon。
- 版本更新為 `1.0.5`。



## V5 修改
- App icon 改為新提供的綠黑方形 Logo。
- 長按更換網址改為「按住 3 秒」才會觸發。
- 版本更新為 1.0.6。


## V5.1 修改
- 改用新提供的綠黑圖示作為 App icon。
- 更換網址功能改為按住 WebView 3 秒才觸發。
- 版本更新為 1.0.7。


## V6 修改

- App 進入背景時暫停 WebView 與 JavaScript timers，降低 ESP 被背景輪詢的壓力。
- App 回到前景時恢復 WebView timers，並延遲重新整理目前頁面一次。
- 保留新 icon、3 秒長按修改網址、系統淺色 / 深色模式。
- 版本更新為 1.0.8。


## V10 修改

- 改成多設備首頁。
- 可新增 SGRE / BMS / WEB 設備。
- 每個設備可設定內網網址與外網備援網址。
- 內網開啟失敗時，WebView 會自動改開外網網址。
- 每個設備卡片可勾選為預設，App 開啟後會直接進入預設設備。
- 設備卡片會顯示簡易狀態；SGRE 會嘗試讀取 `/api/alarm` 與 `/api/live`。
- 掃描頁保留 6053、81、80、1314 掃描。
- 背景警報維持安靜模式，只有 SGRE 預設設備 `/api/alarm` 回傳 `alarm:true` 才通知。
- 不再包含報告 txt/json 檔。


## V11 修改

- 首頁 UI 微調：
  - 標題列高度縮小，並避開 Android 狀態列。
  - 「新增設備 / 搜尋區網」移到底部操作列。
  - 設備卡片移除左側圖案，版面更乾淨。
- 修正 SGRE 設備網址若帶 `/phone`，狀態讀取會錯接成 `/phone/api/alarm` 的問題。
  - 例如 `http://192.168.31.201:81/phone`
  - 狀態 API 會改讀 `http://192.168.31.201:81/api/alarm` 與 `/api/live`
- 保留多設備、預設設備、內外網備援、掃描頁、安靜背景警報。
- 版本更新為 `1.0.14`。


## V12 修改

- 標題改為 `ESP設備管理`，移除閃電圖案。
- 右上角新增圓形 `+`，點擊後可選擇新增設備或搜尋區網。
- 移除底部新增 / 搜尋按鈕，避免被 Android 導航列遮住。
- 設備卡片重新排版：
  - 移除左側圖案。
  - 四角圓角。
  - 預設勾選放在設備名稱同一行右側。
  - 內網網址移到卡片最下方。
  - 只顯示四個內部資訊：電壓、功率、電量、負載。
  - 離線設備整張卡片變灰。
- SGRE `/api/live` 讀取緩衝放大，避免今日電量位於 JSON 後段時被截斷。
- 版本更新為 `1.0.15`。


## V13 修改

- 加入固定 debug 簽章 keystore。
- GitHub Actions 每次編譯出來的 APK 會使用同一把簽章。
- 之後同一台手機可直接覆蓋安裝新版，不需要每次先移除舊版。
- 注意：如果手機裡已安裝的是舊簽章 APK，第一次切換到 V13 仍需要先移除舊 App；之後 V13 以後就可以直接覆蓋更新。
- 版本更新為 `1.0.16`。


## V14 修改

- 右上角 `+` 選單新增：
  - 導出設備
  - 匯入設備
- 導出設備會產生 JSON，可直接複製保存。
- 匯入設備可貼上 JSON，會覆蓋目前設備清單。
- 固定簽章保留；後續可直接覆蓋安裝。
- 版本更新為 `1.0.17`。


## V15 修改

- 修正外網可在 Chrome 開啟，但 App 點進設備空白的問題。
- WebView 開啟設備前會先快速檢查內網網址：
  - 內網可用：開內網
  - 內網不可用且有外網：直接開外網
- 避免手機 4G/外網環境先卡在 192.168.x.x 造成白畫面。
- 保留內網失敗後自動切外網的二次 fallback。
- 固定簽章保留，可直接覆蓋 V13/V14 安裝。
- 版本更新為 `1.0.18`。


## V16 修改

- 卡片網址列改為顯示目前實際可用連線來源：
  - 內網 API 可連時顯示 `內網 ...`
  - 內網失敗、外網 API 可連時顯示 `外網 ...`
  - 都不可連時顯示 `未連線`
- SGRE 卡片的簡易資料讀取會同步判斷內網 / 外網來源。
- BMS / WEB 類設備也會先測內網，失敗後測外網，並更新卡片網址列。
- 固定簽章保留，可直接覆蓋 V13 之後版本。
- 版本更新為 `1.0.19`。


## V16.1 修改

- 重新整理成 GitHub 根目錄直接覆蓋版。
- 不含外層專案資料夾，不含 report txt/json。
- 保留固定簽章 keystore。
- 版本更新為 `1.0.20` / `versionCode 20`，避免同版 APK 安裝器誤判。


## V17 修改

- 右上角 `+` 的匯入功能改成可直接選擇備份檔案。
- 選單現在包含：
  - 新增設備
  - 搜尋區網
  - 導出設備
  - 匯入設備檔案
  - 貼上匯入
- `匯入設備檔案` 會開啟 Android 檔案選擇器，可選擇 `sgre_devices_backup.json`。
- `貼上匯入` 保留作為備援。
- 固定簽章保留，可直接覆蓋 V13 之後版本。
- 版本更新為 `1.0.21`。


## V18 修改

- 保留 V17 的 `匯入設備檔案` 功能。
- 將首頁設備卡片恢復為緊湊排版：
  - 電壓 / 功率 / 電量 / 負載 改回單行顯示。
  - 縮小卡片上下 padding 與項目間距。
  - 網址列維持顯示目前實際連線來源：內網 / 外網 / 未連線。
- 修正匯入失敗提示中的檔名 typo。
- 固定簽章保留，可直接覆蓋 V13 之後版本。
- 版本更新為 `1.0.22` / `versionCode 22`。


## V19 / 1.0.23

- 設備卡片文字與下方網址列縮排再收緊，維持緊湊卡片排版。
- 首頁「電量」改抓電池 SOC，優先讀取 /api/live 的 v_batt_soc / batt_soc / v_battery_soc / battery_soc / v_soc。
- 若設備可連線但沒有抓到任何首頁數值，只顯示「可連線」，不再顯示一整排 --。
- 保留 V17/V18 的設備 JSON 檔案匯入/匯出功能。
- 版本更新為 `1.0.23` / `versionCode 23`。
