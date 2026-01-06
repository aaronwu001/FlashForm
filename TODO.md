# 📝 Project Roadmap & TODO List

## ✅ Completed (MVP Phase)
- [x] **Project Initialization:** Set up Spring Boot with Web, Data JPA, Redis, and AMQP dependencies.
- [x] **Redis Pre-decrement:** Implemented `redisTemplate.opsForValue().decrement()` to handle inventory checks atomically.
- [x] **Message Queue Integration:** Decoupled the submission flow using RabbitMQ (Producer/Consumer model).
- [x] **Database Persistence:** Implemented JPA entities and repositories for storing successful submissions.
- [x] **Concurrency Integration Test:** Created `SeckillIntegrationTest.java` using `ExecutorService` and `CountDownLatch` to simulate 1000 concurrent users. verified eventual consistency between Redis and DB.
- [x] **Idempotency Control:**
    - Prevent the same user from submitting multiple times.
    - *Plan:* Use Redis `Set` (`SADD`, `ISMEMBER`) to track `user_id` per `form_id`.

## 🚧 In Progress / High Priority
- [ ] **Dynamic Schema Validation (Application Layer):**
    - Current implementation accepts raw JSON. Need to validate answers based on user-defined form rules (Required fields, Data types).
    - *Plan:* Implement a `FormValidator` service that checks incoming JSON against a cached Form Definition.

## 📅 Backlog / Future Improvements

### Reliability & Resilience
- [ ] **Dead Letter Queue (DLQ):** Handle cases where the Consumer fails to write to the DB (e.g., DB down or data error). These messages should go to a DLQ for manual retry.
- [ ] **Compensation Mechanism:** If a message is lost or fails in the Consumer, stock in Redis needs to be replenished (Rollback).

### Performance & Security
- [ ] **Rate Limiting:** Implement Token Bucket algorithm (or use Redis Cell) to prevent DDoS attacks or script kiddies.
- [ ] **Redis Scripting (Lua):** Combine "Check User", "Decrement Stock", and "Add User to Set" into a single Lua script to ensure strict atomicity.

### DevOps & Monitoring
- [ ] **Docker Compose:** Create a `docker-compose.yml` file to orchestrate the App, Redis, RabbitMQ, and Postgres with one command.
- [ ] **Observability:** Add Prometheus and Grafana to monitor QPS (Queries Per Second), RabbitMQ queue depth, and JVM metrics.

### Frontend
- [ ] **Simple Demo UI:** A minimal HTML/JS page to allow manual testing of the "Seckill" button.