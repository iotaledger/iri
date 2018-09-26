#!/usr/bin/env bash

echo build starting nginx config

echo replacing ___my.example.com___/$MY_DOMAIN_NAME
echo replacing ___APPLICATION_IP___/$PORT

# Put your domain name into the nginx reverse proxy config.
sed -i "s/___my.example.com___/$MY_DOMAIN_NAME/g" /etc/nginx/nginx.conf
# Add your app's container IP and port into config
sed -i "s/___APPLICATION_PORT___/$PORT/g" /etc/nginx/nginx.conf

echo Firing up nginx in the background.
nginx

# Check user has specified domain name
if [ -z "$MY_DOMAIN_NAME" ]; then
    echo "Need to set MY_DOMAIN_NAME (to a letsencrypt-registered name)."
    exit 1
fi

# This bit waits until the letsencrypt container has done its thing.
# We see the changes here because there's a docker volume mapped.
echo Waiting for folder /etc/letsencrypt/live/$MY_DOMAIN_NAME to exist
while [ ! -d /etc/letsencrypt/live/$MY_DOMAIN_NAME ] ;
do
    sleep 2
done

while [ ! -f /etc/letsencrypt/live/$MY_DOMAIN_NAME/fullchain.pem ] ;
do
	echo Waiting for file fullchain.pem to exist
    sleep 2
done

while [ ! -f /etc/letsencrypt/live/$MY_DOMAIN_NAME/privkey.pem ] ;
do
	echo Waiting for file privkey.pem to exist
    sleep 2
done

echo replacing ___my.example.com___/$MY_DOMAIN_NAME
echo replacing ___APPLICATION_PORT___/$PORT


# Put your domain name into the nginx reverse proxy config.
sed -i "s/___my.example.com___/$MY_DOMAIN_NAME/g" /etc/nginx/nginx-secure.conf
# Add your app's container IP and port into config
sed -i "s/___APPLICATION_PORT___/$PORT/g" /etc/nginx/nginx-secure.conf

#go!
kill $(ps aux | grep '[n]ginx' | awk '{print $2}')
cp /etc/nginx/nginx-secure.conf /etc/nginx/nginx.conf

nginx -g 'daemon off;'
