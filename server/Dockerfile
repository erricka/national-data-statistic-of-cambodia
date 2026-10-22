# Builds the NIS server from source and runs it.
FROM eclipse-temurin:23-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet --version
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:23-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar /app/app.jar
EXPOSE 8010
ENTRYPOINT ["java", "-Xmx1g", "-jar", "/app/app.jar"]
