FROM openjdk:8-jdk
VOLUME /tmp
VOLUME /iri/db
ADD logback.xml /iri/conf/
ADD /target/iri-*.jar iri.jar
RUN touch /iri/conf/application.conf
EXPOSE 14600/udp
EXPOSE 15600
EXPOSE 14265
ENV _JAVA_OPTIONS="-Xms256m -Xmx8g"
ENV REMOTE=true
ENV REMOTE_LIMIT_API="addNeighbors, removeNeighbors, getNeighbors"
ENV API_HOST="0.0.0.0"
ENV PORT=14265
ENV UDP_RECEIVER_PORT=14600
ENV TCP_RECEIVER_PORT=15600
ENV DB_PATH=/iri/db

ENTRYPOINT ["java", "-XX:+DisableAttachMechanism", "-Dconfig.file=/iri/conf/application.conf","-Dlogback.configurationFile=/iri/conf/logback.xml", "-Djava.net.preferIPv4Stack=true", "-jar", "iri.jar"]
