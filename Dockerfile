# --- Build Stage ---
# Use an official Maven image with Java 21 to guarantee a modern Maven version
FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /build

# Cache dependencies first to speed up subsequent builds
COPY pom.xml .
# Notice we no longer need 'yum install', we just run mvn directly
RUN mvn dependency:go-offline

# Copy source and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# --- Production Runtime Stage ---
FROM amazoncorretto:21-alpine

# SECURITY: Create a non-root user and group
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app

# Copy only the compiled jar from the builder stage
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8090

# Start the application with container-aware memory settings
ENTRYPOINT ["java", "-XX:InitialRAMPercentage=50.0", "-XX:MaxRAMPercentage=80.0", "-jar", "app.jar"]