ARG JAVA_IMAGE
FROM ${JAVA_IMAGE}
WORKDIR /app
COPY --chown=10001:10001 application.jar /app/application.jar
COPY --chown=10001:10001 healthcheck.sh /app/healthcheck.sh
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/application.jar"]
