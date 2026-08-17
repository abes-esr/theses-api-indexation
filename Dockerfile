FROM maven:3-eclipse-temurin-17 AS build-image
WORKDIR /build

COPY pom.xml .
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:17-jre AS api-indexation-image
WORKDIR /app

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build-image \
    /build/target/theses-api-indexation-0.1.0-SNAPSHOT.jar \
    /app/theses-api-indexation.jar

ENTRYPOINT ["java", "-jar", "/app/theses-api-indexation.jar"]
