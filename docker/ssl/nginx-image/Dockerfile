FROM nginx:stable

# Install dependencies and then remove apt-get lists to save space
RUN apt-get update && apt-get install -y procps && rm /var/lib/apt/lists/*

COPY start.sh /
COPY nginx.conf /etc/nginx/
COPY nginx-secure.conf /etc/nginx/

COPY dhparams.pem /etc/ssl/private/
CMD /start.sh
