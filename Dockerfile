FROM maven:3.5-jdk-8 as builder
WORKDIR /iri
COPY . /iri
RUN mvn clean package

FROM openjdk:jre-slim
WORKDIR /iri
COPY docker-entrypoint.sh .
RUN ["chmod", "+x", "docker-entrypoint.sh"]

COPY --from=builder /iri/target/iri-1.4.1.4.jar iri.jar
COPY logback.xml /iri
VOLUME /iri

EXPOSE 14265
EXPOSE 14777/udp
EXPOSE 15777

ENV PORT= \
    NEIGHBORS= \
    CONFIG= \
    UPD_RECEIVER_PORT= \
    TCP_RECEIVER_PORT= \
    TESTNET= \
    REMOTE= \
    REMOTE_AUTH= \
    REMOTE_LIMIT_API= \
    SEND_LIMIT= \
    MAX_PEERS= \
    DNS_RESOLUTION_FALSE=

ENTRYPOINT ["./docker-entrypoint.sh"]

