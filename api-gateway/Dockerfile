FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# Copy the JAR that was ALREADY built on your local machine
COPY api-gateway/target/api-gateway-*.jar app.jar

EXPOSE 2323
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]