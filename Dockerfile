FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      curl \
      unzip \
      libheif-examples \
      imagemagick \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system yesihave \
    && useradd --system --gid yesihave --home-dir /app --shell /usr/sbin/nologin yesihave

COPY --from=build --chown=yesihave:yesihave /app/target/*.jar /app/app.jar
ARG TARGETARCH
# Load packaged native libraries from the read-only image, keeping /tmp noexec.
RUN case "$TARGETARCH" in amd64) native_arch=x64 ;; arm64) native_arch=aarch64 ;; *) exit 1 ;; esac \
    && mkdir -p /app/onnx-native \
    && unzip -p /app/app.jar 'BOOT-INF/lib/onnxruntime-*.jar' > /tmp/onnxruntime.jar \
    && unzip -j /tmp/onnxruntime.jar "ai/onnxruntime/native/linux-$native_arch/*.so" -d /app/onnx-native \
    && rm /tmp/onnxruntime.jar
EXPOSE 8080

USER yesihave
ENTRYPOINT ["java", "-Donnxruntime.native.path=/app/onnx-native", "-jar", "/app/app.jar"]
