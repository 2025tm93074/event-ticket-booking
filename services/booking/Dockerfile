
# Build stage
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /app/target/*.jar app.jar

USER app

ENV PORT=8083

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
