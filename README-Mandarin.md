# FlashForm - 高併發秒殺系統後端 (High Concurrency Seckill System)

## 📖 專案簡介
FlashForm 是一個專為處理瞬間高流量設計的表單提交與「秒殺」系統後端。

針對傳統 CRUD 應用在高併發場景下容易遇到的 **資料庫鎖死 (Deadlock)** 與 **超賣 (Overselling)** 問題，本專案採用了多層級架構，利用 **Redis** 進行流量攔截與庫存預扣，並透過 **RabbitMQ** 實現異步削峰，確保系統在高負載下的穩定性與資料最終一致性。

## 🚀 核心架構與解決方案

### 1. 防止超賣 (Overselling Prevention)
- **挑戰：** 傳統資料庫鎖 (Pessimistic/Optimistic Locking) 在高併發下會造成嚴重效能瓶頸，甚至導致請求超時。
- **解決方案：** 利用 **Redis 原子操作 (Atomic Operations, `DECR`)** 在記憶體層進行庫存預減。這確保了超過庫存限制的請求會直接在快取層被擋下，永遠不會對資料庫造成壓力。

### 2. 流量削峰 (Traffic Peak Shaving)
- **挑戰：** 秒殺活動瞬間湧入的寫入請求 (Write Requests) 極易耗盡資料庫連接池，導致系統崩潰。
- **解決方案：** 引入 **RabbitMQ** 作為緩衝層，將「接收請求」與「寫入資料庫」的邏輯解耦 (Decoupling)。
- **流程：** `用戶請求` -> `Redis (檢查庫存與資格)` -> `RabbitMQ (緩衝佇列)` -> `Consumer (平滑消費)` -> `PostgreSQL`。

### 3. 最終一致性 (Eventual Consistency)
- 在分散式架構下，確保 Redis (快取層) 與 PostgreSQL (持久層) 的數據在經過異步處理後達到一致，並透過整合測試驗證此機制。

## 🛠 技術棧 (Tech Stack)
- **語言：** Java 17
- **框架：** Spring Boot 3
- **快取與併發控制：** Redis (Spring Data Redis)
- **訊息隊列：** RabbitMQ
- **資料庫：** PostgreSQL
- **測試：** JUnit 5, MockMvc, ExecutorService (高併發模擬測試)

## ⚙️ 安裝與執行 (Setup)

### 前置需求
- Java 17+
- Maven
- Docker (推薦用於執行基礎設施)

### 1. 啟動基礎設施
使用 Docker 快速啟動 Redis, RabbitMQ 和 PostgreSQL。
```bash
# Docker 啟動指令範例
docker run -d -p 6379:6379 redis
docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:management
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=password postgres
```

### 2. 設定專案
修改 src/main/resources/application.properties，確保資料庫與 Redis 連線資訊與您的本地環境一致。

### 3. 啟動應用程式
```bash
mvn spring-boot:run
```

## 🧪 測試策略
本專案包含一個完整的 端對端整合測試 (E2E Integration Test)，模擬高併發搶購場景。

測試場景： 1000 個並發執行緒 (Threads) 同時搶購 10 個名額。

```bash
# 執行高併發整合測試
mvn test -Dtest=SeckillIntegrationTest
```

預期結果：
- Redis 成功攔截約 990 個請求。
- RabbitMQ 準確傳遞 10 筆訊息。
- 資料庫最終寫入剛好 10 筆訂單，無超賣發生。