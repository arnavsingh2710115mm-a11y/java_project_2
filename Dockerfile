FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY lib/ lib/
COPY src/ src/
RUN mkdir -p classes && javac --release 17 -encoding UTF-8 -cp 'lib/*' -d classes src/quiz/*.java

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system quiz && useradd --system --gid quiz --home-dir /app quiz && mkdir data && chown quiz:quiz data
COPY --from=builder /app/classes/ classes/
COPY --from=builder /app/lib/ lib/
COPY database/ database/
COPY web/ web/
USER quiz
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-XX:MaxRAMPercentage=65", "-Djava.awt.headless=true", "-cp", "classes:lib/*", "quiz.WebServer"]
