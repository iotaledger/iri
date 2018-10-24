#!/usr/bin/env bash

set -e
echo "Setting up Let's Encrypt SSL for IRI using nginx proxy_pass"

while getopts 'e:d:t' flag; do
  case "${flag}" in
    e) EMAIL="${OPTARG}" ;;
    d) DOMAIN="${OPTARG}" ;;
    t) TOS="${OPTARG}" ;;
  esac
done

# Email
if [[ -z "$EMAIL" ]]; then
  echo "Please enter your email (used by certbot for urgent renewal and security notices):"
  read EMAIL
  if [[ -z "$EMAIL" ]]; then
    echo "Error: You must enter an email"
    exit 1
  fi
fi

# Domain
if [[ -z "$DOMAIN" ]]; then
  echo "Please enter a domain that points to this system's IP:"
  read DOMAIN
  if [[ -z "$DOMAIN" ]]; then
    echo "Error: You must enter a domain"
    exit 1
  fi
fi

# Terms of Service
if [[ "$TOS" != "y" ]]; then
  echo "Please read the Terms of Service at https://letsencrypt.org/documents/LE-SA-v1.2-November-15-2017.pdf. You must agree in order to register with the ACME server at https://acme-v01.api.letsencrypt.org/directory. Please enter "y" to accept the ToS."
  read TOS
  if [[ "$TOS" != "y" ]]; then
    echo "Error: You must agree to the ToS in order to continue"
    exit 1
  fi
fi

# Modify docker-compose.yml
sed -i "s/__DOMAIN__/$DOMAIN/g" ./docker-compose.yml
sed -i "s/__EMAIL__/$EMAIL/g" ./docker-compose.yml
