FROM maven:3-jdk-8-onbuild
CMD [ "java", "-jar", "./target/locations2-runnable.war" ]
EXPOSE 8080
