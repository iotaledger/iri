FROM ubuntu:18.04 as local_stage_java
MAINTAINER giorgio@iota.org

# Install Java
ARG JAVA_VERSION=8u181-1
RUN \
  apt-get update && \
  apt-get install -y software-properties-common --no-install-recommends && \
  echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y oracle-java8-installer=${JAVA_VERSION}~webupd8~1 --no-install-recommends && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk8-installer

# Define commonly used JAVA_HOME variable
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# install maven on top of java stage
FROM local_stage_java as local_stage_build
ARG MAVEN_VERSION=3.5.3
ARG USER_HOME_DIR="/root"
ARG SHA=b52956373fab1dd4277926507ab189fb797b3bc51a2a267a193c931fffad8408
ARG BASE_URL=https://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
  && echo "${SHA}  /tmp/apache-maven.tar.gz" | sha256sum -c - \
  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
  && rm -f /tmp/apache-maven.tar.gz \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"

COPY docker/mvn-entrypoint.sh /usr/local/bin/mvn-entrypoint.sh
COPY docker/settings-docker.xml /usr/share/maven/ref/

VOLUME "$USER_HOME_DIR/.m2"

# install build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
        git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /iri

COPY . /iri
RUN mvn clean package

# execution image
FROM local_stage_java

RUN apt-get update && apt-get install -y --no-install-recommends \
        jq curl socat \
    && rm -rf /var/lib/apt/lists/*

COPY --from=local_stage_build /iri/target/iri*.jar /iri/target/
COPY docker/entrypoint.sh /

# Java related options. Defaults set as below
ENV JAVA_OPTIONS="-XX:+UnlockExperimentalVMOptions -XX:+DisableAttachMechanism -XX:InitiatingHeapOccupancyPercent=60 -XX:G1MaxNewSizePercent=75 -XX:MaxGCPauseMillis=10000 -XX:+UseG1GC"
ENV JAVA_MIN_MEMORY 2G
ENV JAVA_MAX_MEMORY 4G

# Additional custom variables. See DOCKER.md for details
ENV DOCKER_IRI_JAR_PATH "/iri/target/iri*.jar"
ENV DOCKER_IRI_REMOTE_LIMIT_API "interruptAttachToTangle, attachToTangle, addNeighbors, removeNeighbors, getNeighbors"

# Setting this to 1 will have socat exposing 14266 and pointing it on
# localhost. See /entrypoint.sh
# !!! DO NOT DOCKER EXPOSE (-p) 14266 as the remote api settings
#     will not be applied on that port !!!
# You also have to maintain $DOCKER_IRI_MONITORING_API_PORT_DESTINATION
# based on the actual API port exposed via IRI
ENV DOCKER_IRI_MONITORING_API_PORT_ENABLE 0
ENV DOCKER_IRI_MONITORING_API_PORT_DESTINATION 14265

WORKDIR /iri/data
ENTRYPOINT [ "/entrypoint.sh" ]
