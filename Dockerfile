FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/nexbridge-1.0.0.jar app.jar
RUN addgroup -S nexbridge && adduser -S nexbridge -G nexbridge
RUN mkdir -p /certs /data/vault && chown nexbridge:nexbridge /certs /data
USER nexbridge
EXPOSE 8080 8443
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["java","-jar","-Dspring.profiles.active=prod","app.jar"]
