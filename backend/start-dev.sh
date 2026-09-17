#!/usr/bin/env bash
# Starts the backend with the dev profile. Safe to run from any directory.
set -euo pipefail

# Change into the folder this script lives in (backend/), so spring-dotenv finds
# backend/.env and Maven finds pom.xml wherever the script was invoked from. Resolving
# it from the script's location rather than a fixed path keeps it working if the repo
# is cloned somewhere other than ~/codes/resume-tailor.
cd "$(dirname "${BASH_SOURCE[0]}")"

mvn spring-boot:run -Dspring-boot.run.profiles=dev
