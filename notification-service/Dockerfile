# Multi-stage build: dependencies are resolved first so the layer cache survives source edits.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S lanka && adduser -S lanka -G lanka
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER lanka
EXPOSE 9005
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:${SERVER_PORT:-9005}/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
