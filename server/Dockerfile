FROM eclipse-temurin:17-jdk-alpine


WORKDIR /app


COPY server/build/libs/*.jar app.jar


EXPOSE 8080

hl
ENTRYPOINT ["java", "-jar", "app.jar"]