# 📝 專案開發路線圖 & 待辦清單 (Project Roadmap)

## ✅ 已完成 (MVP 階段)
- [x] **專案初始化：** 建立 Spring Boot 專案，整合 Web, Data JPA, Redis, AMQP 依賴。
- [x] **Redis 預減庫存：** 實作 `redisTemplate.opsForValue().decrement()` 進行原子性庫存扣除。
- [x] **訊息隊列整合：** 使用 RabbitMQ 實現生產者/消費者模式，解耦下單流程。
- [x] **資料庫持久化：** 設計 JPA Entity 與 Repository，儲存搶購成功的訂單資料。
- [x] **冪等性控制 (Idempotency)：** 使用 Redis Set 與互斥鎖 (Mutex Lock) 防止重複下單。
- [x] **應用層動態驗證 (Dynamic Schema Validation)：** 實作 `FormValidator`，根據快取中的定義檢查輸入資料格式與活動時間。
- [x] **系統韌性與自我修復 (Resilience)：**
    - 實作 **互斥鎖 (Mutex Lock)** 防止快取擊穿 (Cache Breakdown)。
    - 實作 **快取重建 (Cache Rebuild)** 機制，當 Redis 資料遺失時自動從 DB 撈回。
    - 處理 **部分快取失效 (Partial Cache Miss)** 的庫存回補。
- [x] **標準化：** 將 Controller, Service 與測試的時間處理統一為 **UTC** 標準。
- [x] **完整場景測試：** 完成 `SeckillScenarioTest`，涵蓋快樂路徑、驗證失敗、重複下單、快取重建、幽靈名單等 6 大場景。

## 🚧 進行中 / 高優先級
- [ ] **死信隊列 (Dead Letter Queue, DLQ)：** 當 Consumer 寫入失敗時進行重試處理。
- [ ] **Docker Compose：** 撰寫 `docker-compose.yml` 一鍵啟動所有服務。

## 📅 未來規劃 (Backlog)

### 效能與安全性
- [ ] **限流機制 (Rate Limiting)：** 實作權杖桶算法。
- [ ] **Lua 腳本優化：** 將檢查與扣款邏輯封裝成單一 Lua Script。

### 前端
- [ ] **簡易 Demo UI：** 製作一個極簡的 HTML 頁面進行展示。