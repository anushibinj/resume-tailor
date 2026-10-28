#!/bin/bash

# This script is used to redeploy a Docker container with the latest image.
# We are running docker compose up -d --build twice to ensure that the container
# is fully recreated and redeployed with the latest image.
git pull && docker compose up -d --build && docker compose up -d --build
