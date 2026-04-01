FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY src src

RUN chmod +x mvnw && ./mvnw -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/target/finance-tracker-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-XX:+UseVirtualThreads", "-jar", "app.jar"]
