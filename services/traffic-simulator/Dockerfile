FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY services/ services/
RUN mvn -B -ntp -pl services/traffic-simulator -am -DskipTests package

FROM eclipse-temurin:21.0.13_9-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/services/traffic-simulator/target/traffic-simulator-1.0.0.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
EXPOSE 9084
HEALTHCHECK --interval=15s --timeout=3s --start-period=2m --retries=8 CMD wget -q -O - http://127.0.0.1:9084/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
