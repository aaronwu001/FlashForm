## 🚀 核心架構與解決方案

### 1. 高併發控制與防止超賣
- **Redis 原子操作：** 利用 `DECR` 在記憶體層預扣庫存，將無效請求攔截在快取層。
- **互斥鎖防擊穿 (Mutex Lock)：** 實作 `setIfAbsent` 鎖定機制，防止快取失效瞬間 (Cache Breakdown) 導致大量請求打掛資料庫。確保同一時間只有一個執行緒能回源重建快取。

### 2. 系統韌性與自我修復 (Self-Healing)
- **快取自動重建：** 當 Redis 資料遺失 (Cache Miss) 時，系統會自動從 PostgreSQL 撈回規則與庫存並回寫 Redis。
- **部分失效處理：** 能夠偵測並修復「Meta 存在但 Quota 遺失」的極端情況。

### 3. 流量削峰 (Traffic Peak Shaving)
- **異步處理：** 引入 RabbitMQ 將寫入邏輯解耦。
- **流程：** `用戶請求` -> `驗證與庫存檢查` -> `RabbitMQ 緩衝` -> `Consumer 消費` -> `PostgreSQL`。

### 4. 動態驗證與國際化
- **動態 Schema 驗證：** 根據資料庫定義的規則動態檢查 JSON 輸入。
- **UTC 標準化：** 全系統時間比較與儲存皆採用 UTC 標準，確保跨時區一致性。

## 🧪 測試策略
本專案包含完整的場景測試 `SeckillScenarioTest`，涵蓋以下 6 大關鍵場景：
1.  **快樂路徑 (Happy Path)：** 驗證全鏈路暢通 (Redis -> MQ -> DB)。
2.  **驗證失敗 (Validation Fail)：** 確保無效資料被攔截。
3.  **重複下單 (Duplicate)：** 驗證冪等性機制。
4.  **規則與庫存救援 (Rebuild)：** 模擬 Redis 資料遺失後的自動修復。
5.  **幽靈名單救援 (Ghost User)：** 驗證歷史訂單名單的搬運與鎖定邏輯。
6.  **部分快取失效 (Partial Miss)：** 驗證庫存單獨遺失時的自動補償。