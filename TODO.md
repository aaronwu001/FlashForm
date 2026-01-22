# 📝 Project Roadmap & TODO List

## ✅ Completed (MVP & Core Logic)

* [x] **Project Initialization:** Set up Spring Boot with Web, Data JPA, Redis, and AMQP.
* [x] **Redis Pre-decrement:** Implemented atomic decrement for high-concurrency inventory control.
* [x] **Message Queue Integration:** Decoupled submission flow using RabbitMQ (Producer/Consumer).
* [x] **Database Persistence:** JPA implementation for persistent PostgreSQL storage.
* [x] **Idempotency & Resilience:** Redis Sets for duplicate check and Mutex Locking for cache protection.
* [x] **Dynamic Schema Validation:** Implemented `FormValidator` for dynamic JSON field checks.
* [x] **UTC Standardization:** Standardized all time-related logic across the system to UTC.
* [x] **Performance Verification (JMeter):** - Achieved **746.27 QPS** with **100% success rate**.
* Verified system stability and zero-overselling under 1000+ concurrency.


* [x] **RESTful API Refactoring:** - Moved `formId` to path variables (`/api/forms/{formId}/submit`).
* Simplified Request Body by removing redundant ID fields.


* [x] **High-Coverage Unit Testing:** - Completed **Mockito-based Unit Tests** for `SeckillService`.
* Successfully validated core logic branches: Success, Duplicate, No Quota, and Time Validation.
* Decoupled business logic verification from unstable infrastructure (Async/Database Race Conditions).



## 🚀 High Priority (Pre-Enlistment Sprint 🪖)

* [ ] **Simple Frontend Demo:** - Create a minimal HTML/JavaScript page in `src/main/resources/static`.
* Implement a "Seckill Button" and real-time status display (Queueing / Success / Sold Out).


* [ ] **Docker Compose & Cloud Prep:** - Finalize `docker-compose.yml` for one-click environment setup.
* Deploy to a cloud platform (e.g., AWS EC2, Render, or Railway) to create a shareable live demo link.



## 📅 Backlog (Future Improvements)

* [ ] **Dead Letter Queue (DLQ):** Handle consumer persistence failures for mission-critical reliability.
* [ ] **Rate Limiting:** Implement Token Bucket or Leaky Bucket algorithm at the API Gateway level.
* [ ] **Redis Optimization (Lua):** Consolidate inventory check and idempotency check into a single Lua script for better atomicity.
* [ ] **Admin Dashboard:** A basic UI to create forms and monitor submission data.
