# https://hub.docker.com/_/amazoncorretto
FROM amazoncorretto:17-alpine-jdk
WORKDIR /opt/app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]