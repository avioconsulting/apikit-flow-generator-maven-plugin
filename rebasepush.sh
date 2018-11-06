#!/bin/sh
set -e

echo "Pushing to general AVIO Nexus"
./gradlew clean uploadArchives

echo "Updating AVIO DFW bitbucket repo"
# AVIO Nexus
git checkout mule4.1/dfw_from_outside_their_net
git rebase mule4.1/master
git push --force

echo "Pushing to AVIO Nexus DFW"
./gradlew clean uploadArchives

echo "Now updating DFW customer code"
git checkout mule4.1/dfw
git rebase mule4.1/master
git push --force dfwgithub mule4.1/dfw:mule4.1/master
git push --force origin mule4.1/dfw

echo Now you can push to DFW Artifactory via Gradle on your VPN VM and then switch back...
