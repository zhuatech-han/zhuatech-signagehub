# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /work
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2,sharing=locked sh -c 'for attempt in 1 2 3; do mvn -B -q -Dmaven.wagon.http.retryHandler.count=5 dependency:go-offline && exit 0; sleep $((attempt * 5)); done; exit 1'
COPY src src
RUN --mount=type=cache,target=/root/.m2,sharing=locked sh -c 'for attempt in 1 2 3; do mvn -B -q -Dmaven.wagon.http.retryHandler.count=5 spotless:check clean package && exit 0; sleep $((attempt * 5)); done; exit 1'
FROM maven:3.9-eclipse-temurin-21
RUN groupadd --system signagehub && useradd --system --gid signagehub --home-dir /app signagehub
RUN for sourcefile in /etc/apt/sources.list /etc/apt/sources.list.d/*.sources; do if [ -f "$sourcefile" ]; then sed -i 's|http://ports.ubuntu.com|https://ports.ubuntu.com|g;s|http://archive.ubuntu.com|https://archive.ubuntu.com|g;s|http://security.ubuntu.com|https://security.ubuntu.com|g' "$sourcefile"; fi; done \
    && apt-get -o Acquire::Retries=3 -o Acquire::https::Timeout=30 update \
    && apt-get -o Acquire::Retries=3 -o Acquire::https::Timeout=30 install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN mkdir /app/media && chown signagehub:signagehub /app/media
COPY --from=build --chown=signagehub:signagehub /work/target/zhuatech-signagehub.jar app.jar
USER signagehub
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
