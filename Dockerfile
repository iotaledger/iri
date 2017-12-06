FROM maven:3.5-jdk-8 as builder
WORKDIR /iri
COPY . /iri
RUN mvn clean package

FROM openjdk:jre-slim
WORKDIR /iri
COPY --from=builder /iri/target/iri-*.jar iri.jar
COPY logback.xml /iri
VOLUME /iri

EXPOSE 14265
EXPOSE 14777/udp
EXPOSE 15777

ENV _JAVA_OPTIONS="-Xms256m -Xmx8g"
ENV REMOTE=true
ENV REMOTE_LIMIT_API="addNeighbors, removeNeighbors, getNeighbors"
ENV PORT=14265
ENV UDP_RECEIVER_PORT=14777
ENV TCP_RECEIVER_PORT=15777

ENTRYPOINT ["/usr/bin/java", "-XX:+DisableAttachMechanism", "-Dlogback.configurationFile=/iri/conf/logback.xml", "-Djava.net.preferIPv4Stack=true", "-jar", "iri.jar"]
