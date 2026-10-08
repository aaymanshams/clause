FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 clauseiq && mkdir -p /app/data && chown clauseiq /app/data
COPY --from=build /app/target/clauseiq-*.jar app.jar
USER clauseiq
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
