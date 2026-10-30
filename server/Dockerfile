# Build stage — context is the repository root (Render uses the same context).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY server/pom.xml ./pom.xml
RUN mvn -q -DskipTests dependency:go-offline
COPY server/src ./src
RUN mvn -q -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/iskolar-server-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
