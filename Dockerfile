FROM eclipse-temurin:24-jdk

WORKDIR /app

COPY src ./src

RUN mkdir -p out \
    && javac -d out src/com/sequencescheduler/Main.java \
    && cp -r src/web out/web

EXPOSE 10000

CMD ["java", "-cp", "out", "com.sequencescheduler.Main"]
