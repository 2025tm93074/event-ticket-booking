
# Stage 1: Build the application
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Download dependencies separately to improve build caching
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Copy source and compile
COPY src ./src
RUN mvn -q -B package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as a non-root user
RUN addgroup -S app && adduser -S app -G app

COPY --from=build /app/target/*.jar app.jar

USER app

ENV PORT=8081
EXPOSE 8081

# Container health check
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -qO- http://localhost:${PORT}/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
