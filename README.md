# FlashForm - High-Performance Seckill & Form Backend

## 🎯 Project Overview
FlashForm is a robust backend engine designed to handle **extreme traffic spikes** in limited-inventory scenarios.

In traditional web applications, when thousands of users try to submit a form or buy an item simultaneously (a "Thundering Herd" event), databases often lock up, crash, or accidentally sell more items than available (Overselling).

FlashForm solves this by acting as a **high-speed traffic valve**. It guarantees:
1.  **Zero Overselling:** Strict quota enforcement even with 10k+ concurrent requests.
2.  **System Stability:** Protects the database from direct traffic shocks.
3.  **Fairness:** Processes requests in an orderly manner using queues.

## 💡 Typical Use Cases
This system is ideal for any scenario requiring **"Limited Quantity + High Concurrency + Strict Time Window"**:

* **⚡ E-Commerce Flash Sales:** Limited-time product launches (e.g., iPhone drops, sneaker releases).
* **🎫 Ticketing Systems:** Concert tickets or sports event booking where seats sell out in seconds.
* **🎓 Campus Systems:** University course registration or dormitory room selection.
* **🏥 Medical & Public Services:** Vaccine appointment scheduling or government subsidy applications.
* **🎁 Marketing Campaigns:** Limited-time lucky draws or "first come, first served" giveaways.

---

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

## 🚀 API Usage & Setup Guide

### 1. Environment Setup
Ensure all Docker containers are running and healthy.
```bash
docker compose up -d --build

```

### 2. Initialize Form (Admin Setup)

Before users can submit data, the form **Metadata (Schema)** and **Quota** must be initialized in Redis.

* **Note:** The `type` field MUST be one of: `TEXT`, `NUMBER`, `EMAIL`. Using other types (e.g., `STRING`) will result in a `400 Bad Request`.

**Option A: Using PowerShell Script (Recommended for Dev)**
Modify `$formBody` in `setup_test.ps1`:

```powershell
$formBody = @{
    ownerId = "Admin"
    quota = 100
    # format: [{"name": "field_key", "type": "ENUM_TYPE", "required": boolean}]
    schemaJson = '[{"name":"email","type":"TEXT","required":true}]' 
}

```

**Option B: Manual Initialization (via Redis CLI)**
If you don't have an Init API exposed yet, you can manually set the schema in Redis:

```bash
docker exec -it flashform-redis redis-cli

# 1. Set Quota
SET form:quota:1 "100"

# 2. Set Metadata (Schema)
# Key: form:meta:{formId}
# Field: schema
# Value: JSON String (Escaped)
HSET form:meta:1 schema "[{\"name\":\"email\",\"type\":\"TEXT\",\"required\":true}]"

```

### 3. Submit Form (User Action)

This is the endpoint for high-concurrency testing.

* **Endpoint:** `POST /api/form/submit`
* **Content-Type:** `application/json`
* **Payload Example:**

```json
{
  "formId": "1",
  "userId": "user_123",
  "answers": {
    "email": "test@example.com"
  }
}

```

### 4. Reset Logic (For Retesting)

To clear previous submissions and restart the test:

1. **Flush DB:** `TRUNCATE TABLE submissions;` (in Postgres)
2. **Reset Redis:** Run the Initialization script again (it overwrites the quota).
3. **Purge Queue:** (Optional) Purge RabbitMQ queues if consumers are stopped.



