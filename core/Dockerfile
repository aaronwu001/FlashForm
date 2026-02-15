# Use lightweight JDK 17 on Alpine Linux
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# --- Layer Caching Strategy ---
# Copy Maven wrapper and descriptor first to cache dependencies
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Grant execution permissions and download dependencies
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline

# --- Build Application ---
# Copy source code
COPY src ./src

# Build the JAR, skipping tests to speed up deployment
RUN ./mvnw package -DskipTests

# Run the application
CMD ["java", "-jar", "target/core-0.0.1-SNAPSHOT.jar"]