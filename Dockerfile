FROM openjdk:8-jdk
VOLUME /tmp
ADD logback.xml /iri/conf/
ADD /target/iri-*.jar iri.jar
RUN touch /iri/conf/application.conf
EXPOSE 14265
EXPOSE 14777/udp
EXPOSE 15777
ENV _JAVA_OPTIONS="-Xms256m -Xmx8g"
ENV REMOTE=true
ENV REMOTE_LIMIT_API="addNeighbors, removeNeighbors, getNeighbors"
ENV PORT=14265
ENV UDP_RECEIVER_PORT=14777
ENV TCP_RECEIVER_PORT=15777

ENTRYPOINT ["java", "-XX:+DisableAttachMechanism", "-Dconfig.file=/iri/conf/application.conf","-Dlogback.configurationFile=/iri/conf/logback.xml", "-Djava.net.preferIPv4Stack=true", "-jar", "iri.jar"]
