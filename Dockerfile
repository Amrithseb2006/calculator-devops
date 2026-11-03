# Runtime-only image: Jenkins/Maven builds the jar, Docker just packages it.
FROM eclipse-temurin:17-jre-alpine


LABEL version="2.0.0" \
      description="DevOps Demo Java Application"
      

# Run as a non-root user
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY target/devops-demo.jar app.jar

ARG APP_VERSION=2.0.0
ENV APP_VERSION=${APP_VERSION} \
    PORT=8080

USER app
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]