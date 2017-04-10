FROM maven:3-jdk-8-onbuild

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

ONBUILD ADD . /usr/src/app

ONBUILD RUN mvn install -DskipTests=true
CMD ["/usr/bin/java", "-jar", "target/iri-1.1.3.5.jar", "-p", "$PORT", "-n", "$NEIGHBORS"]
