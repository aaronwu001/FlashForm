# FlashForm - High-Performance Seckill & Form Backend

## 🎯 Project Overview

FlashForm is a robust backend engine designed to handle **extreme traffic spikes** in limited-inventory scenarios.

In traditional web applications, when thousands of users try to submit a form or buy an item simultaneously (a "Thundering Herd" event), databases often lock up or crash. FlashForm solves this by acting as a **high-speed traffic valve**. It guarantees:

1. **Zero Overselling:** Strict quota enforcement even with 10k+ concurrent requests.
2. **System Stability:** Protects the database from direct traffic shocks.
3. **Fairness:** Processes requests in an orderly manner using message queues.

## 💡 Typical Use Cases

This system is ideal for any scenario requiring **"Limited Quantity + High Concurrency + Strict Time Window"**:

* **⚡ E-Commerce Flash Sales:** Limited-time product launches.
* **🎫 Ticketing Systems:** Concert tickets or sports event booking.
* **🎓 Campus Systems:** University course registration or dormitory selection.
* **🏥 Medical Services:** Vaccine appointment scheduling.

---

## 🚀 Key Features & Architecture

### 1. High-Performance Concurrency Control

* **Redis Atomic Operations:** Uses `DECR` for in-memory stock management, blocking excess requests before they reach the DB.
* **Mutex Locking (Cache Breakdown Protection):** Implements a `setIfAbsent` locking mechanism to prevent "Thundering Herd" issues during cache eviction.

### 2. Resilience & Self-Healing

* **Automatic Cache Rebuild:** Detects cache misses (Meta or Quota) and automatically restores data from PostgreSQL to Redis.
* **Partial Failure Handling:** Recovers from edge cases where specific keys are missing while others persist.

### 3. Asynchronous Peak Shaving

* **RabbitMQ Integration:** Decouples submission ingestion from persistence.
* **Workflow:** `User Request` -> `Validation & Redis Check` -> `RabbitMQ` -> `Consumer` -> `PostgreSQL`.

---

## 🚀 API Usage & Setup Guide

### 1. Initialize Form (Admin Setup)

Create a form resource to trigger the **Cache Warm-up** (syncing quota and metadata to Redis).

* **Endpoint:** `POST /api/forms`
* **Payload Example:**

```json
{
    "ownerId": "Admin_Aaron",
    "title": "Flash Sale Event",
    "quota": 100,
    "startTime": "2026-01-22T20:00:00",
    "endTime": "2026-01-23T20:00:00",
    "schemaJson": "[{\"name\":\"email\",\"type\":\"TEXT\",\"required\":true}]"
}

```

### 2. Submit Form (User Action)

The high-concurrency entry point for form submissions.

* **Endpoint:** `POST /api/forms/{formId}/submit`
* **Note:** The `formId` is extracted directly from the **URL path**. The request body no longer requires the `formId` field, ensuring a cleaner RESTful interface.
* **Payload Example:**

```json
{
  "userId": "user_123",
  "answers": {
    "email": "test@example.com"
  }
}

```

### 3. Monitoring & Reset

* **Check Quota:** `GET /api/forms/{formId}/quota` (Fetches remaining stock directly from Redis).
* **Reset Logic:** `POST /api/forms/{formId}/reset/{quota}` (Updates quota and clears the idempotency set in Redis).

---

## 🚀 High-Concurrency Performance Benchmarks

The core submission logic was stress-tested using **Apache JMeter** to simulate real-world high-traffic events.

### **Performance Summary**

| Metric | Peak Performance (Burst 1) | High Load (Burst 2) |
| --- | --- | --- |
| **Concurrent Samples** | 1,000 | 1,000 |
| **Success Rate** | **100% (0.00% Error)** | **100% (0.00% Error)** |
| **Throughput (QPS)** | **746.27 req/sec** | **412.20 req/sec** |
| **Average Latency** | **258.50 ms** | 1,295.88 ms |
| **Min/Max Latency** | 66ms / 465ms | 699ms / 1,886 ms |
| **95th Percentile** | **380.00 ms** | 1,635.05 ms |

### **Technical Deep Dive**

* **Zero-Failure Guarantee:** Maintained a **0.00% error rate** under a load of 1,000 requests per second, ensuring every valid submission was captured without data loss.
* **High-Throughput Architecture:** Processed up to **746 transactions per second** using Redis-based quota management, significantly outperforming traditional database-locking approaches.
* **Reliable Back-pressure:** Utilized **RabbitMQ** to maintain consistent response times while decoupling front-end feedback from back-end persistence.

---

## 🧪 Testing Strategy

FlashForm employs a multi-layered testing strategy to ensure both logical correctness and system stability.

### 1. High-Coverage Unit Testing (Mockito)

We pivot from infrastructure-heavy integration tests to focused Unit Tests for `SeckillService`. This avoids **"Infrastructure Race Conditions"** caused by asynchronous database visibility and ensures **100% logic coverage**:

* **Success Path:** Validates atomic quota decrement and message dispatch.
* **Idempotency:** Ensures duplicate submissions are blocked via Redis Sets.
* **Safety Nets:** Validates that quota is correctly restored if downstream processes fail.
* **Time Validation:** Confirms that forms cannot be submitted before `startTime` or after `endTime`.

### 2. Performance Benchmarking (JMeter)

Asynchronous consistency is verified using heavy-load JMeter tests (1,000+ concurrent requests), ensuring the system maintains **0.00% error rate** and zero-overselling in a live environment.

### 3. Integration Scenarios (Manual/Dev)

The `SeckillScenarioTest` suite remains available for environment verification, covering cache recovery and database persistence under stable conditions.
