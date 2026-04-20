# ── Build stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven
RUN mvn -q -DskipTests package

# ── Runtime ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S flowforge && adduser -S flowforge -G flowforge
COPY --from=build /app/target/*.jar app.jar
USER flowforge
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
