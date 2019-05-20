## OAuth configuration

### mysql set up

download -> install -> run mysql on you machine

loggin with root to create trias user create databases and grant privileges
```bash  
mysql -u root
create user 'trias'@'%' identified by '123456';
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

cd scripts/front_end/trias-oauth/oauth-resource/src/main/resources/db 
mysql -u trias -p trias_cli
source trias_cli-init.sql 
```

### OAuth server / client set up

start oauth and oauth cli
```bash
cd scripts/front_end/trias-oauth/oauth-resource/target/
java -jar oauth-resource-1.0-SNAPSHOT.jar 

cd scripts/front_end/trias-oauth/oauth-server/target 
java -jar oauth-server-1.0-SNAPSHOT.jar
```

## To start server

This document is based ontemplate for [vue-cli](https://github.com/vuejs/vue-cli)

``` bash
# install dependencies
npm install

# serve with hot reload at localhost:8081
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
#### nginx-conf
- see the config below
 
        ……
        http{
            ……
            include       mime.types;
            default_type  application/octet-stream;
            gzip  on;
            gzip_min_length 1k;
            gzip_buffers 4 16k;
            gzip_http_version 1.0;
            gzip_comp_level 6;
            gzip_types text/plain application/javascript application/x-javascript text/javascript text/xml text/css;
            gzip_disable "MSIE [1-6]\.";
            gzip_vary on;
            ……
            server {
                ……
                # rootpath
                root /usr/share/nginx/html/;
                location /api/ {
                    proxy_set_header  Host $host;
                    proxy_headers_hash_max_size 1024;
                    proxy_headers_hash_bucket_size 128;
                    proxy_set_header  X-Forwarded-For $proxy_add_x_forwarded_for ;
                    proxy_set_header Accept-Encoding "";
                    proxy_pass http://goserveraddress:8000/;
                }
            }
            ……
        }
- mkdir -p /usr/share/nginx/html/trias-dag
- npm run build
- cp -r dist/* /usr/share/nginx/html/trias-dag/
        
        You should use your own nginx root config
## License
[MIT](http://opensource.org/licenses/MIT)

