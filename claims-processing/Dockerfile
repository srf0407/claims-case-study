FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp -DskipTests package \
    && jar="$(find target -maxdepth 1 -type f -name 'claims-processing-*.jar' ! -name '*.original' -print)" \
    && test -n "$jar" \
    && cp "$jar" /build/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home-dir /app --no-create-home app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
