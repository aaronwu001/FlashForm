# 📝 專案開發路線圖 & 待辦清單 (Project Roadmap)

## ✅ 已完成 (MVP 階段)
- [x] **專案初始化：** 建立 Spring Boot 專案，整合 Web, Data JPA, Redis, AMQP 依賴。
- [x] **Redis 預減庫存：** 實作 `redisTemplate.opsForValue().decrement()` 進行原子性庫存扣除。
- [x] **訊息隊列整合：** 使用 RabbitMQ 實現生產者/消費者模式 (Producer/Consumer)，解耦下單流程。
- [x] **資料庫持久化：** 設計 JPA Entity 與 Repository，儲存搶購成功的訂單資料。
- [x] **併發整合測試：** 使用 `ExecutorService` 與 `CountDownLatch` 模擬 1000 人同時搶購，驗證 Redis 與 DB 的最終一致性。
- [x] **冪等性控制 (Idempotency)：**
    - 防止同一位使用者重複下單或因網路延遲導致重複扣款。
    - *方案：* 使用 Redis Set (`SADD`, `ISMEMBER`) 紀錄已購買的 `user_id`。

## 🚧 進行中 / 高優先級
- [ ] **應用層動態驗證 (Dynamic Schema Validation)：**
    - 目前系統接收 Raw JSON。需實作驗證器，根據使用者定義的表單規則 (如：必填欄位、資料類型) 檢查輸入資料。
    - *方案：* 實作 `FormValidator` Service，比對傳入資料與快取中的 Form Definition。

## 📅 未來規劃 (Backlog)

### 可靠性與容錯 (Reliability)
- [ ] **死信隊列 (Dead Letter Queue, DLQ)：** 當 Consumer 寫入 DB 失敗時 (例如 DB 當機或資料錯誤)，將訊息轉移至 DLQ 進行人工重試，防止訂單遺失。
- [ ] **庫存回補機制 (Compensation)：** 若 Consumer 處理失敗，需透過補償交易將 Redis 中的庫存加回。

### 效能與安全性 (Performance & Security)
- [ ] **限流機制 (Rate Limiting)：** 實作權杖桶算法 (Token Bucket) 防止惡意刷單或 DDoS 攻擊。
- [ ] **Lua 腳本優化：** 將「檢查用戶」、「扣庫存」、「加入用戶紀錄」封裝成單一 Lua Script，確保絕對的原子性執行。

### 維運與監控 (DevOps)
- [ ] **Docker Compose：** 撰寫 `docker-compose.yml` 一鍵啟動 App 與所有依賴服務。
- [ ] **系統可觀測性 (Observability)：** 接入 Prometheus 與 Grafana 監控 QPS、RabbitMQ 堆積量與 JVM 指標。

### 前端
- [ ] **簡易 Demo UI：** 製作一個極簡的 HTML 頁面與「搶購按鈕」以進行手動測試展示。