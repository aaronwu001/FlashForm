# FlashForm Performance Testing Guide

This document outlines the standard operating procedure for conducting high-concurrency load tests on the FlashForm API using Apache JMeter. It covers environment setup, data initialization, execution, and verification.

## 1. Test Environment Overview

* **Infrastructure:** Docker Compose (Single Node).
* **Components:** Spring Boot (Core), Redis (Cache), RabbitMQ (Async Messaging), PostgreSQL (DB).
* **Testing Tool:** Apache JMeter 5.6.3+.
* **Target Scenario:** 1,000 concurrent users submitting forms within a 1-second window.

## 2. Prerequisites

Ensure all services are running and healthy before starting the test:

```bash
# Start the full stack
docker compose up -d --build

# Verify all containers are active
docker ps

```

## 3. Phase 1: Data Initialization (Form Setup)

Before launching the JMeter test, you must create a form resource to trigger the **Cache Warm-up** (syncing quota and metadata to Redis).

**⚠️ Critical Note on Schema Types:**
The `schemaJson` field only accepts `TEXT`, `NUMBER`, or `EMAIL`. Using `STRING` will result in a `400 Bad Request`.

### Initialization Script (PowerShell)

Run this script to create a test target with a **quota of 100**.

```powershell
# 1. Set UTC Time
$nowUtc = [DateTime]::UtcNow
$startTime = $nowUtc.AddHours(-1).ToString("yyyy-MM-ddTHH:mm:ss")
$endTime = $nowUtc.AddHours(5).ToString("yyyy-MM-ddTHH:mm:ss")

# 2. Create Payload (Endpoint: /api/forms)
$formBody = @{
    ownerId = "Admin_Tester"
    title = "JMeter Load Test"
    quota = 100
    startTime = $startTime
    endTime = $endTime
    schemaJson = '[{"name":"email","type":"TEXT","required":true}]' 
} | ConvertTo-Json

# 3. Send Request
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/forms" -Method Post -Body $formBody -ContentType "application/json"

# 4. Extract generated Form ID
$formId = $response -replace "[^0-9]", ""
Write-Host "✅ Target Created. Form ID: $formId"

```

---

## 4. Phase 2: JMeter Configuration

Load the script located at `core/jmeter/1k_qps_test.jmx`.

### Key Settings

* **Thread Group:**
* Number of Threads: `1000`
* Ramp-up Period: `1` second


* **HTTP Request (Updated Path):**
* Method: `POST`
* **Path:** `/api/forms/${formId}/submit`
* **Body Data:**


```json
{
    "userId": "user_${__RandomString(10,abcdefghijklmnopqrstuvwxyz,)}",
    "answers": {
        "email": "stress_test@example.com"
    }
}

```


*Note: The SubmissionController now extracts the formId directly from the URL path.*

---

## 5. Phase 3: Execution

### CLI Mode (Recommended)

To ensure the most accurate results without GUI overhead, run the test via command line:

```bash
jmeter -n -t "./core/jmeter/1k_qps_test.jmx" -l "./results/result.jtl"

```

---

## 6. Phase 4: Success Criteria & Verification

A successful benchmark must meet the following three criteria based on validated results:

### 1. JMeter Performance Metrics

* **Error Rate:** Must be **0.00%**.
* **Throughput (QPS):** Expected range between **240 and 746 req/sec** depending on local environment.
* **Latency:** Average response time should be approximately **258ms to 1300ms** under 1,000 concurrency.

### 2. Data Consistency (Anti-Overselling)

Verify that the database recorded exactly 100 submissions (matching the quota).

```bash
# Check PostgreSQL record count
docker exec -it flashform-db psql -U postgres -d flashform_db -c "SELECT COUNT(*) FROM submissions WHERE form_id = '1';"

```

### 3. Quota Status API

Check the remaining quota via the refactored management endpoint:

* **Endpoint:** `GET /api/forms/{formId}/quota`

---

## 7. Troubleshooting: Resetting the Test

If you need to clear the environment for a new test run, use the reset endpoint:

* **Endpoint:** `POST /api/forms/{formId}/reset/{quota}`
* **Function:** Resets the quota in Redis and clears the submission set (Idempotency Key), allowing the same users to submit again.
