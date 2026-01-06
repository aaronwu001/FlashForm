# FlashForm - High Concurrency Seckill System

## 📖 Introduction
FlashForm is a high-concurrency form submission and "seckill" (flash sale) system backend designed to handle massive traffic spikes while ensuring data consistency.

Unlike traditional CRUD applications, this system addresses the **C10K problem** and **Overselling** issues by implementing a multi-layered architecture using **Redis** for traffic interception and **RabbitMQ** for asynchronous peak shaving.

## 🚀 Key Features & Architecture

### 1. Overselling Prevention (Concurrency Control)
- **Challenge:** Traditional DB locking (Pessimistic/Optimistic) creates bottlenecks under high concurrency, leading to slow response times or overselling.
- **Solution:** Utilized **Redis Atomic Operations (`DECR`)** to pre-decrement stock in memory. This ensures that requests exceeding the quota are blocked at the cache layer, never reaching the database.

### 2. Traffic Peak Shaving (Asynchronous Processing)
- **Challenge:** Direct database writes during a flash sale can crash the database due to connection exhaustion.
- **Solution:** Implemented **RabbitMQ** to decouple the submission receiving logic from the persistence logic.
- **Flow:** `User Request` -> `Redis (Check Quota)` -> `RabbitMQ (Buffer)` -> `Consumer` -> `PostgreSQL`.

### 3. Eventual Consistency
- Ensured data consistency between Redis (Cache) and PostgreSQL (Persistence) through robust consumer acknowledgement mechanisms and integration testing.

## 🛠 Tech Stack
- **Language:** Java 17
- **Framework:** Spring Boot 3
- **Cache & Locking:** Redis (Spring Data Redis)
- **Message Queue:** RabbitMQ
- **Database:** PostgreSQL
- **Testing:** JUnit 5, MockMvc, ExecutorService (Concurrency Simulation)

## ⚙️ Setup & Installation

### Prerequisites
- Java 17+
- Maven
- Docker (Recommended for running infrastructure)

### 1. Start Infrastructure
Use Docker to spin up Redis, RabbitMQ, and PostgreSQL.
```bash
# Example Docker commands
docker run -d -p 6379:6379 redis
docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:management
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=password postgres
```

### 2. Configure Application
Update src/main/resources/application.properties with your local credentials if different from defaults.

### 3. Run the Application
```bash
mvn spring-boot:run
```

## 🧪 Testing
This project includes a comprehensive Integration Test that simulates high concurrency.

Scenario: 1000 concurrent threads competing for 10 items.

```bash
# Run the High Concurrency Integration Test
mvn test -Dtest=SeckillIntegrationTest
```

Expected Result:
- Redis successfully intercepts 990 requests.
- RabbitMQ delivers exactly 10 messages.
- Database contains exactly 10 records.