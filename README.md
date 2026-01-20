## 🚀 Key Features & Architecture

### 1. High-Performance Concurrency Control
- **Redis Atomic Operations:** Uses `DECR` for in-memory stock management, blocking excess requests before they reach the DB.
- **Mutex Locking (Cache Breakdown Protection):** Implemented a locking mechanism (`setIfAbsent`) to prevent "Thundering Herd" issues when cache expires or is evicted. Only one thread accesses the DB to rebuild the cache.

### 2. Resilience & Self-Healing
- **Automatic Cache Rebuild:** The system detects cache misses (Meta or Quota) and automatically restores data from PostgreSQL to Redis.
- **Partial Failure Handling:** Capable of recovering from edge cases where only specific keys (e.g., quota) are missing.

### 3. Asynchronous Peak Shaving
- **RabbitMQ Integration:** Decouples submission ingestion from persistence.
- **Flow:** `User Request` -> `Validation & Stock Check` -> `RabbitMQ` -> `Consumer` -> `PostgreSQL`.

### 4. Dynamic Validation (UTC Standard)
- **Schema Validation:** Validates JSON input against dynamic rules defined in the database.
- **UTC Standardization:** All time comparisons and storage use UTC to ensure global consistency.

## 🧪 Testing
The project includes a robust test suite `SeckillScenarioTest` covering 6 critical scenarios:
1.  **Happy Path:** Full flow verification (Redis -> MQ -> DB).
2.  **Validation Fail:** Rejection of invalid data types.
3.  **Duplicate Submission:** Idempotency verification.
4.  **Meta/Quota Rebuild:** Verifies system recovery after cache deletion.
5.  **Ghost User Rebuild:** Ensures database migration logic works if Redis user sets are lost.
6.  **Partial Cache Miss:** Verifies quota restoration logic.