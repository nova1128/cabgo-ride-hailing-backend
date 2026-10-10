FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY cab-app/pom.xml cab-app/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY notification-service/src notification-service/src
RUN mvn -q -B -pl notification-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/notification-service/target/app.jar app.jar
EXPOSE 8081
# Tuned for small free-tier containers (512 MB RAM, tiny CPU): serial GC, light JIT, capped memory.
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-XX:MaxRAMPercentage=60", "-XX:ReservedCodeCacheSize=48m", "-Xss512k", "-jar", "/app/app.jar"]
