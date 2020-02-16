FROM openjdk:8-alpine

COPY target/uberjar/disaster.jar /disaster/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/disaster/app.jar"]
