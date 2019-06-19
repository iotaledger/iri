## OAuth configuration

### mysql set up

download -> install -> run mysql on you machine

loggin with root to create trias user create databases and grant privileges
```bash  
mysql -u root
create user 'trias'@'%' identified by 'trias@123';
create database trias_cli;
create database trias;
grant all privileges on trias.* to trias@'%';
grant all privileges on trias_cli.* to trias@'%';
```

source tables into database
```bash
mvn package
cd scripts/front_end/trias-oauth/oauth-server/src/main/resources/db/
mysql -u trias -p trias
source trias_server-init.sql
source trias-server_ddl.sql


cd scripts/front_end/trias-oauth/oauth-resource/src/main/resources/db/
mysql -u trias -p trias_cli
source trias_cli-init.sql 
source trias_cli_user_ddl.sql

```

### OAuth server / client set up

start oauth and oauth cli
```bash
$TRIAS_OAUTH_CLIENT=/opt/trias/oauth/client
mkdir -p $TRIAS_OAUTH_CLIENT
cp scripts/front_end/trias-oauth/oauth-resource/target/oauth-resource-1.0-SNAPSHOT.jar $TRIAS_OAUTH_CLIENT
cp scripts/front_end/trias-oauth/oauth-resource/src/main/resources/application.yml   $TRIAS_OAUTH_CLIENT
cp scripts/front_end/trias-oauth/oauth-resource/src/main/resources/logback-spring.xml  $TRIAS_OAUTH_CLIENT
cd $TRIAS_OAUTH_CLIENT
java -jar oauth-resource-1.0-SNAPSHOT.jar  &

$TRIAS_OAUTH_SERVER=/opt/trias/oauth/server
mkdir -p $TRIAS_OAUTH_SERVER
cp scripts/front_end/trias-oauth/oauth-server/target/oauth-server-1.0-SNAPSHOT.jar $TRIAS_OAUTH_SERVER
cp scripts/front_end/trias-oauth/oauth-server/src/main/resources/application.yml  $TRIAS_OAUTH_SERVER
cp scripts/front_end/trias-oauth/oauth-server/src/main/resources/logback-spring.xml  $TRIAS_OAUTH_SERVER
cd $TRIAS_OAUTH_SERVER
java -jar oauth-server-1.0-SNAPSHOT.jar &
```

## To start server

This document is based ontemplate for [vue-cli](https://github.com/vuejs/vue-cli)

``` bash

# modify the config
vi scripts/front_end/web/src/common/config/config.js
# Replace the ip address with the local ip address

# install dependencies
npm install

# serve with hot reload at localhost:9081
npm run dev

# build for production with minification
npm run build

```

## Folder structure
* build - webpack config files

* config - webpack config files

* dist - build

* src -your app  
  * api
  * assets
  * common
  * components - your vue components
  * mock
  * styles
  * views - your pages
  * vuex
  * App.vue
  * main.js - main file
  * routes.js

* static - static assets

## How to run in dev/product environment
### First server
- cd ~/iri/scripts/front_end/server
- go run main.go
### dev
- modify proxy file ~/config/proxyConfig.js
    - modify target with your address
- modify ~/src/common/config/config.js
    - modify the serer list you need
- npm run dev

### product
- mkdir -p /usr/share/nginx/html/trias-dag
- npm run build
- cp -r dist/* /usr/share/nginx/html/trias-dag/
        
You should use your own nginx root config  and configure the nginx.conf first.
## License
[MIT](http://opensource.org/licenses/MIT)
