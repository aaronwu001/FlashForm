# FlashForm Performance Testing Guide

This document outlines the standard operating procedure for conducting high-concurrency load tests on the FlashForm API using Apache JMeter. It includes environment setup, data initialization, execution steps, and troubleshooting for common schema errors.

## 1. Test Environment Overview

* **Infrastructure:** Docker Compose (Single Node)
* **Components:** Spring Boot (Core), Redis (Cache), RabbitMQ (Async Messaging), PostgreSQL (DB).
* **Testing Tool:** Apache JMeter 5.6.3+
* **Target Scenario:** 1,000 Concurrent Users submitting forms within 1 second.

## 2. Prerequisites

Ensure the following services are running and healthy:

```bash
# Start the full stack
docker compose up -d --build

# Verify RabbitMQ and Redis are active
docker ps

```

## 3. Phase 1: Data Initialization (The Target)

Before launching the JMeter attack, you must create a form with a specific quota in Redis.

**⚠️ Critical Note on Schema Types:**
The `schemaJson` field accepts specific Enum types only. Using undefined types will result in a `400 Bad Request`.

* **Allowed:** `TEXT`, `NUMBER`, `EMAIL`
* **Invalid:** `STRING`, `DATE`, `BOOL`

### Initialization Script (PowerShell)

Run the following script to create a form with **100 quota** for testing.

```powershell
# Setup variables
$nowUtc = [DateTime]::UtcNow
$startTime = $nowUtc.AddHours(-1).ToString("yyyy-MM-ddTHH:mm:ss")
$endTime = $nowUtc.AddHours(5).ToString("yyyy-MM-ddTHH:mm:ss")

# Create Payload
# NOTE: Ensure 'type' is set to 'TEXT', not 'STRING'
$formBody = @{
    ownerId = "Admin_Tester"
    title = "JMeter Load Test"
    quota = 100
    startTime = $startTime
    endTime = $endTime
    schemaJson = '[{"name":"email","type":"TEXT","required":true}]' 
} | ConvertTo-Json

# Send Request
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/form/init" -Method Post -Body $formBody -ContentType "application/json"

# Output Form ID
Write-Host "✅ Target Created. Form ID: $($response.formId)"

```

## 4. Phase 2: JMeter Configuration

Load the script located at `core/jmeter/1k_qps_test.jmx`.

### Key Configurations

* **Thread Group:**
* Number of Threads: `1000`
* Ramp-up Period: `1` second
* Loop Count: `1`


* **HTTP Request:**
* Method: `POST`
* Path: `/api/form/submit`
* **Body Data:**
```json
{
    "formId": "1",
    "userId": "user_${__RandomString(10,abcdefghijklmnopqrstuvwxyz,)}",
    "answers": {
        "email": "stress_test@example.com"
    }
}

```


* *Note:* The `${__RandomString}` function is required to simulate unique users and avoid "Repeated Submission" errors.



## 5. Phase 3: Execution

### Option A: GUI Mode (Debug Only)

Use this for verifying configuration or running small smoke tests.

1. Open JMeter GUI.
2. Press the **Green Start Button**.
3. Monitor the **Summary Report**.

### Option B: CLI Mode (Recommended for Load Testing)

Use this for actual performance benchmarking to avoid GUI resource overhead.

```bash
jmeter -n -t "./core/jmeter/1k_qps_test.jmx" -l "./results/result.jtl"

```

## 6. Phase 4: Verification (Success Criteria)

A successful test must meet **all three** criteria below:

### 1. JMeter Report

* **Error %:** Must be `0.00%`.
* **Throughput:** Should reflect system capacity (e.g., >200 RPS on local dev).

### 2. RabbitMQ Status

Check the queue status to ensure all messages were consumed.

* **URL:** `http://localhost:15672`
* **Queue:** `form.submission.queue`
* **Metric:** `Ready` and `Unacked` messages must return to **0** after the test.

### 3. Data Consistency (The "Overselling" Check)

Verify that the database did not record more submissions than the allowed quota (100).

```bash
# Check PostgreSQL record count
docker exec -it flashform-db psql -U postgres -d flashform_db -c "SELECT COUNT(*) FROM submissions WHERE form_id = '1';"

```

**Expected Result:** `100` (Exactly matches the quota).

---

## 7. Troubleshooting: The "FieldType" Mismatch

During development, a common issue may arise regarding the `FieldType` Enum.

### Symptom

* API returns `400 Bad Request`.
* Response Body: `❌ JSON Parsing Error: Invalid value 'STRING'. Allowed values are: [TEXT, NUMBER, EMAIL]`.

### Cause

The project uses strict Enum validation. Historically, some scripts used `"type": "STRING"`, which is **not** a valid `FieldType` in the Java backend.

### Resolution

1. **Check Initialization Data:** Ensure your Redis setup script or SQL seed data uses `"type": "TEXT"`.
2. **Clean Redis:** If "dirty data" persists, flush the Redis cache to remove old schema definitions.
```bash
docker exec -it flashform-redis redis-cli DEL form:meta:1

```