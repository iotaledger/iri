FROM iotacafe/maven:3.5.4.oracle8u181.1.webupd8.1.1-1
RUN mkdir /oauth_web
COPY ./ /oauth_web
WORKDIR /oauth_web/trias-oauth
ARG HOST_IP=localhost
ENV HOST_IP ${HOST_IP} 
RUN sed -i "s/localhost/${HOST_IP}/g" ../web/config/proxyConfig.js
RUN sed -i "18s/localhost/${HOST_IP}/g" oauth-resource/src/main/resources/application.yml 
RUN sed -i "s/localhost/${HOST_IP}/g" oauth-server/src/main/resources/application.yml 
RUN sed -i "s/localhost/${HOST_IP}/g" /oauth_web/nginx.conf
RUN mvn clean package
RUN add-apt-repository ppa:longsleep/golang-backports -y
RUN apt-get update && apt-get install golang-go -y \
    && rm -rf /var/lib/apt/lists/*
RUN curl -sL https://deb.nodesource.com/setup_10.x -o nodesource_setup.sh \
    && bash nodesource_setup.sh \
    && apt install -y nodejs
RUN mkdir -p /opt/trias/oauth/client  /opt/trias/oauth/server \
    && mv /oauth_web/trias-oauth/oauth-server/target/oauth-server-1.0-SNAPSHOT.jar /opt/trias/oauth/server/oauth-server-1.0-SNAPSHOT.jar \
    && mv /oauth_web/trias-oauth/oauth-resource/target/oauth-resource-1.0-SNAPSHOT.jar /opt/trias/oauth/client/oauth-resource-1.0-SNAPSHOT.jar \
    && mv /oauth_web/trias-oauth/oauth-server/src/main/resources/application.yml /opt/trias/oauth/server/application.yml \
    && mv /oauth_web/trias-oauth/oauth-server/src/main/resources/logback-spring.xml /opt/trias/oauth/server/logback-spring.xml \
    && mv /oauth_web/trias-oauth/oauth-resource/src/main/resources/application.yml /opt/trias/oauth/client/application.yml  \
    && mv /oauth_web/trias-oauth/oauth-resource/src/main/resources/logback-spring.xml /opt/trias/oauth/client/logback-spring.xml
WORKDIR /oauth_web/web
RUN npm install --unsafe-perm=true \
    && npm run build  \
    && mkdir -p /usr/share/nginx/html/trias-dag/
WORKDIR /oauth_web/web/dist/
RUN cp  -r index.html static /usr/share/nginx/html/trias-dag/
WORKDIR /oauth_web/server
RUN go get -u github.com/triasteam/noderank
RUN apt-get install software-properties-common -y \
    && add-apt-repository ppa:nginx/stable -y \
    && apt-get update \
    && apt-get install nginx -y \
    && rm -rf /var/lib/apt/lists/*
RUN mv /oauth_web/nginx.conf  /etc/nginx/nginx.conf
CMD ["sh", "/oauth_web/entrypoint.sh"]
