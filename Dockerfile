FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 afterimage
WORKDIR /app
COPY --from=build /workspace/target/afterimage-*.jar app.jar
RUN mkdir -p /app/media && chown -R afterimage:afterimage /app
USER afterimage
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
