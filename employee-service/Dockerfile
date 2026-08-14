FROM eclipse-temurin:17-jdk-alpine as build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY ../common ../common
COPY ../pom.xml ../pom.xml
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
