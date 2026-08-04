# Stage 1: Build the application
# Use the official Eclipse Temurin image for Java 25 as the builder
FROM eclipse-temurin:25-jdk AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the Gradle executable and wrapper properties
COPY gradlew .
COPY gradle/ gradle/

# Copy the build configuration files
COPY build.gradle.kts settings.gradle.kts ./

# Make the Gradle wrapper executable
RUN chmod +x ./gradlew

# Download dependencies (this layer will be cached unless build files change)
# We ignore errors here in case some dependencies can't be resolved without the source code
RUN ./gradlew dependencies --no-daemon || true

# Copy the application source code
COPY src/ src/

# Build the application, skipping tests to speed up the process
RUN ./gradlew build -x test --no-daemon

# Stage 2: Create the runtime image
# Use a minimal Java runtime environment (JRE) image to keep the final image size small
FROM eclipse-temurin:25-jre-alpine

# Set the working directory inside the container
WORKDIR /app

# Create a non-root user and group for better security (Alpine uses addgroup/adduser)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built JAR file from the builder stage
# We use a wildcard to match the generated jar file, avoiding hardcoding the version
COPY --from=builder --chown=spring:spring /app/build/libs/*.jar app.jar

# Expose the port the application runs on (typically 8080 for Spring Boot)
EXPOSE 8080

# Specify the command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
