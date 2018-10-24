# IRI with SSL for Docker

This folder contains the files necessary to set up an IRI Docker container with an SSL certificate, using `nginx` proxy-pass to direct remote requests to IRI.

## Prerequisites
You'll need the following in order to get started:
- Docker
- [Docker Compose](https://docs.docker.com/compose/install/) (This might already be included with Docker, depending on your installation method)
- A domain name with DNS records set up

## Getting Started
1. Clone this repository:
```
git clone https://github.com/iotaledger/iri
```

2. Navigate to this directory:
```
cd iri/docker/ssl
```

3. Run the setup script and follow the prompts:
```
./start.sh
```

4. Build the images:
```
docker-compose build
```

5. Start the containers:
```
docker-compose up
```

## Testing
If you want to test the application before running in a production environment, ensure that you add the `--staging` flag to the `certbot` command on line 16 of `docker-compose.yml`. This will tell `certbot` to obtain a test certificate and allow you to exceed the normal [rate limits](https://letsencrypt.org/docs/rate-limits/). Note that the test certificate will be untrusted by most systems by default.

## Acknowledgements
- Docker Compose setup based on [le-docker-compose](https://bitbucket.org/automationlogic/le-docker-compose/overview) by Automation Logic
- `nginx` configuration based on [auto-nginx-https](https://github.com/eukaryote31/auto-nginx-https) by eukaryote31
