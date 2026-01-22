# FlashForm - High-Performance Seckill & Form Backend

## 🎯 Project Overview

FlashForm is a robust backend engine designed to handle **extreme traffic spikes** in limited-inventory scenarios.

FlashForm acts as a **high-speed traffic valve**. It guarantees:

1. **Zero Overselling:** Strict quota enforcement even with 10k+ concurrent requests.
2. **System Stability:** Protects the database from direct traffic shocks.
3. **Fairness:** Processes requests in an orderly manner using queues.

---

## 🚀 API Usage & Setup Guide

### 1. Initialize Form (Admin Setup)

建立表單資源並觸發 **Cache Warm-up**，系統會同步更新 Redis 中的 Quota 與 Meta 數據。

* **Endpoint:** `POST /api/forms`
* **Payload Example:**

```json
{
    "ownerId": "Admin_Aaron",
    "title": "Flash Sale Test",
    "quota": 100,
    "startTime": "2026-01-22T20:00:00",
    "endTime": "2026-01-23T20:00:00",
    "schemaJson": "[{\"name\":\"email\",\"type\":\"TEXT\",\"required\":true}]"
}

```

### 2. Submit Form (User Action)

此接口專為高併發環境設計，僅負責核心提交邏輯。

* **Endpoint:** `POST /api/forms/{formId}/submit`
* **Performance:** 經測試在 1000 併發下達成 **0% 錯誤率** 與 **746.27 QPS**。
* **Payload Example:**

```json
{
  "userId": "user_123",
  "answers": {
    "email": "test@example.com"
  }
}

```

### 3. Monitor & Reset (Admin Control)

管理員可以用來監控即時剩餘配額，或在測試結束後重置環境。

* **Check Quota:** `GET /api/forms/{formId}/quota`
* 直接從 Redis 讀取當前剩餘名額。


* **Reset Environment:** `POST /api/forms/{formId}/reset/{quota}`
* 重設配額數量並清除 Redis 中的已提交名單 (Idempotency Key)。



---

## 🚀 High-Concurrency Performance Benchmarks

The core submission logic was stress-tested using **Apache JMeter**.

### **Performance Summary**

| Metric | Peak Performance | High Load Performance |
| --- | --- | --- |
| **Concurrent Samples** | 1,000 | 1,000 |
| **Success Rate** | **100% (0.00% Error)** | **100% (0.00% Error)** |
| **Throughput (QPS)** | **746.27 req/sec** | **412.20 req/sec** |
| **Average Latency** | **258.50 ms** | 1,295.88 ms |
| **Min/Max Latency** | 66ms / 465ms | 699ms / 1,886 ms |
| **95th Percentile** | **380.00 ms** | 1,635.05 ms |

### **Technical Deep Dive**

* **Zero-Failure Guarantee:** Even under a heavy load of 1,000 requests per second, the system maintained a **0.00% error rate**.
* **High-Throughput Architecture:** By implementing **Redis-based quota management**, the system successfully processed up to **746 transactions per second**.
* **Reliable Back-pressure:** The use of **RabbitMQ** allowed consistent response times (Avg 1.3s under stress) while decoupling front-end feedback from database persistence.

---

## 🧪 Testing Scenarios

`SeckillScenarioTest` covers 6 critical business cases:

1. **Happy Path:** Verification of full flow (Redis -> MQ -> DB).
2. **Validation Fail:** Rejection of invalid data types.
3. **Duplicate Submission:** Idempotency verification via Redis Set.
4. **Meta/Quota Rebuild:** System recovery after cache eviction.
5. **Ghost User Rebuild:** Database-to-Cache migration for user lists.
6. **Partial Cache Miss:** Automated restoration of missing Quota keys.
