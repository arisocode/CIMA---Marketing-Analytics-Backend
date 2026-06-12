# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml ./
# Download dependencies first for caching
RUN mvn dependency:go-offline -B
COPY src ./src
COPY gateway ./gateway
RUN mvn package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache tini curl \
    && addgroup -g 1001 -S springuser \
    && adduser -S springuser -u 1001 -G springuser
USER springuser

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 3003
ENTRYPOINT ["tini", "--"]
CMD ["java", "-jar", "app.jar"]
