FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system tagme && useradd --system --gid tagme tagme
WORKDIR /app
COPY --from=build --chown=tagme:tagme /workspace/build/libs/app.jar app.jar
USER tagme
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
