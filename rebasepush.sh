#!/bin/sh
set -e

echo "Pushing to general AVIO Nexus"
./gradlew clean uploadArchives

echo "Now updating customer code"
git checkout customer_branch
git rebase master
git push --force customer_origin customer_branch:master

echo "Deploying to customer"
./gradlew clean uploadArchives
