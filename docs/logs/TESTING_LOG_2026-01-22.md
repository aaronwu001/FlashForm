# FlashForm Performance Testing & Troubleshooting Log

**Date:** January 22, 2026
**Author:** Aaron Wu
**Subject:** 1k QPS Stress Test, "Silent Failure" Diagnosis, and System Robustness Improvements

## 1. Objective

To validate the stability and data consistency of the `FlashForm` high-concurrency submission API (`/api/form/submit`) under a load of 1,000 concurrent users using Apache JMeter.

## 2. Test Environment

* **Infrastructure:** Docker Compose (Single Node)
* **Stack:** Spring Boot (Core), Redis (Cache), RabbitMQ (Async Processing), PostgreSQL (Persistence).
* **Tooling:** Apache JMeter 5.6.3
* **Scenario:** 1,000 threads (users) with a 10-second ramp-up period.

## 3. Issue Chronology & Resolution

### Issue A: JMeter Configuration Error

* **Symptom:** JMeter failed to start tests with a `Could not delete existing file` error pointing to the `/bin` directory.
* **Cause:** The "Filename" field in the *View Results Tree* and *Summary Report* listeners was incorrectly configured to point to a directory path instead of a specific file (e.g., `.jtl`).
* **Resolution:** Cleared the filename configuration in the JMX script, allowing JMeter to run in GUI mode without file locks.

### Issue B: The "Silent Failure" (200 OK but No Data)

* **Symptom:**
* JMeter reported **100% Success** (HTTP 200 OK).
* **RabbitMQ:** Message rates remained flat at 0 msg/s.
* **PostgreSQL:** The `submissions` table remained empty (0 records) despite 1,000 requests.


* **Diagnosis:**
* The Controller logic lacked proper `try-catch` blocks or global exception handling.
* Exceptions occurred *before* the message was published to RabbitMQ, but the API still returned a "Success" response to the client.



### Issue C: Root Cause Analysis - Enum Mismatch

* **Log Analysis:** Docker logs revealed a `com.fasterxml.jackson.databind.exc.InvalidFormatException`.
* **Specific Error:** `Cannot deserialize value of type FieldType from String "STRING": not one of the values accepted for Enum class: [TEXT, NUMBER, EMAIL]`.
* **Root Cause:**
* The Java `FieldType` Enum only defined `TEXT`, `NUMBER`, and `EMAIL`.
* The Redis key `form:meta:1` (Metadata) contained a schema definition with `"type": "STRING"`.
* This mismatch caused the application to crash during the schema validation phase of the request.



### Issue D: Data Contamination (The "Zombie" Bug)

* **Observation:** Even after fixing the code and restarting the application, the error persisted.
* **Cause:**
* **Docker Volumes:** Redis data was persisted on the disk. Even after restarting containers, the "dirty" data (`type: "STRING"`) remained in the cache.
* **Source of Contamination:** The PowerShell initialization script (`setup_test.ps1`) contained a hardcoded JSON string using the wrong type (`STRING` instead of `TEXT`).


* **Resolution:**
1. **Fix Script:** Updated `setup_test.ps1` to use the correct `TEXT` type.
2. **Clean Redis:** Executed `DEL form:meta:1` (and `FLUSHALL`) via Redis CLI.
3. **Clean Restart:** Performed `docker compose down -v` to remove old volumes.



## 4. System Improvements (Robustness)

To prevent future "Silent Failures" and improve debuggability, the following architectural changes were implemented:

### 1. Global Exception Handling

Introduced a `@RestControllerAdvice` component to catch unexpected exceptions.

* **Action:** Intercepts `InvalidFormatException`.
* **Result:** Returns **HTTP 400 Bad Request** with a descriptive error message (e.g., *"Invalid value 'STRING'. Allowed values are..."*) instead of a generic 500 error or a misleading 200 OK.

### 2. Strict Enum Validation

Decided against using "Default Enum Values" (e.g., `UNKNOWN`). Enforcing strict type validation ensures data integrity prevents "dirty data" from entering the persistence layer.

## 5. Final Test Results

After applying the fixes and cleaning the environment, the stress test was re-executed:

* **JMeter Result:** 1,000 Requests, 0% Error Rate.
* **Throughput:** ~262 RPS (Limited by local hardware resources).
* **RabbitMQ:** Observed distinct spike in message publication and consumption rates.
* **Data Integrity:** Validated via SQL: `SELECT count(*) FROM submissions;` returned **1,000** records.

## 6. Conclusion

The system successfully handled the concurrency target. The critical vulnerability (Silent Failure) has been patched, and the system now provides clear feedback on schema validation errors. The testing infrastructure is now reliable for future scaling tests.