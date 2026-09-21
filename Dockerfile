FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN mkdir -p bin && javac -d bin $(find src/main/java -name "*.java")