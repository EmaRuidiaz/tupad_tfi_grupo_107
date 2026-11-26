FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

COPY database ./database
COPY backend/pom.xml ./backend/pom.xml

WORKDIR /app/backend
RUN mvn dependency:go-offline -B

COPY backend/src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/backend/target/backend-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
