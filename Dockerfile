FROM maven:3.5-jdk-8

WORKDIR /iri
COPY . /iri

#RUN git clone https://github.com/iotaledger/iri.git /iri/
RUN mvn clean package
RUN mv /iri/target/iri-1.3.2.2.jar /tmp
RUN rm -rf *
RUN rm -rf /tmp/junit*
RUN mv /tmp/iri*.jar iri.jar
COPY logback.xml /iri

VOLUME /iri

EXPOSE 14265
EXPOSE 14777/udp
EXPOSE 15777

CMD ["/usr/bin/java", "-XX:+DisableAttachMechanism", "-Xmx8g", "-Xms256m", "-Dlogback.configurationFile=/iri/conf/logback.xml", "-Djava.net.preferIPv4Stack=true", "-jar", "iri.jar", "-p", "14265", "-u", "14777", "-t", "15777", "--remote", "--remote-limit-api", "\"addNeighbors, removeNeighbors, getNeighbors\"", "$@"]
