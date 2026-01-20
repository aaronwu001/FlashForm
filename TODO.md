# 📝 Project Roadmap & TODO List

## ✅ Completed (MVP Phase)
- [x] **Project Initialization:** Set up Spring Boot with Web, Data JPA, Redis, and AMQP dependencies.
- [x] **Redis Pre-decrement:** Implemented `redisTemplate.opsForValue().decrement()` to handle inventory checks atomically.
- [x] **Message Queue Integration:** Decoupled the submission flow using RabbitMQ (Producer/Consumer model).
- [x] **Database Persistence:** Implemented JPA entities and repositories for storing successful submissions.
- [x] **Idempotency Control:** Prevent duplicate submissions using Redis Sets and Mutex Locks.
- [x] **Dynamic Schema Validation:** Implemented `FormValidator` to check JSON input against cached form definitions (schema & time).
- [x] **Resilience & Recovery:**
    - Implemented **Mutex Locking** (`setIfAbsent`) to prevent Cache Breakdown (Thundering Herd).
    - Implemented **Cache Rebuild** logic to automatically fetch Meta/Quota from DB on cache miss.
    - Implemented **Partial Cache Miss** handling (Quota restoration).
- [x] **Standardization:** Standardized time handling to **UTC** across Controller, Service, and Tests.
- [x] **Comprehensive Testing:** Created `SeckillScenarioTest` covering Happy Path, Validation Fail, Duplicates, Cache Rebuild, and Ghost Users.

## 🚧 In Progress / High Priority
- [ ] **Dead Letter Queue (DLQ):** Handle cases where the Consumer fails to write to the DB.
- [ ] **Docker Compose:** Create a `docker-compose.yml` to orchestrate App, Redis, RabbitMQ, and Postgres.

## 📅 Backlog / Future Improvements

### Performance & Security
- [ ] **Rate Limiting:** Implement Token Bucket algorithm.
- [ ] **Redis Scripting (Lua):** Optimize atomicity by combining check-and-set operations.

### Frontend
- [ ] **Simple Demo UI:** A minimal HTML/JS page for manual testing.