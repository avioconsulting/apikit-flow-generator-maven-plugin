#!/bin/sh
set -e

echo "Pushing to general AVIO Nexus"
./gradlew clean uploadArchives

echo "Updating AVIO DFW bitbucket repo"
# AVIO Nexus
git checkout dfw_from_outside_their_net
git rebase master
git push --force

echo "Pushing to AVIO Nexus DFW"
./gradlew clean uploadArchives

echo "Now updating DFW customer code"
git checkout dfw
git rebase master
git push --force dfw dfw:master
git push --force origin dfw

echo Now you can push to DFW Artifactory via Gradle on your VPN VM and then switch back...
