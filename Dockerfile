FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      libheif-examples \
      imagemagick \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system yesihave \
    && useradd --system --gid yesihave --home-dir /app --shell /usr/sbin/nologin yesihave

COPY --from=build --chown=yesihave:yesihave /app/target/*.jar /app/app.jar
EXPOSE 8080

USER yesihave
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
