# 使用輕量級的 JDK 17
FROM eclipse-temurin:17-jdk-alpine

# 設定工作目錄
WORKDIR /app

# 複製 Maven Wrapper 和 pom.xml (利用 Docker Layer Caching 加速)
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# 下載依賴 (這步會被 Cache 住，除非你改 pom.xml)
# 確保 mvnw 有執行權限
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline

# 複製原始碼
COPY src ./src

# 打包 (跳過測試以加速啟動，測試我們在 CI/CD 或本地做就好)
RUN ./mvnw package -DskipTests

# 執行 App (根據你的 jar 檔名可能需要微調，通常是 target/*.jar)
CMD ["java", "-jar", "target/core-0.0.1-SNAPSHOT.jar"]