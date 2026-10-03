FROM eclipse-temurin:11-jre

WORKDIR /app

COPY target/master-app.jar app.jar

EXPOSE 8000

CMD ["java", "-jar", "app.jar"]