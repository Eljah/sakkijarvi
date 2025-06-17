#!/usr/bin/env bash
set -e
MAVEN_VERSION=3.9.6
MAVEN_DIR="$HOME/maven"
if [ ! -d "$MAVEN_DIR" ]; then
  echo "Downloading Maven $MAVEN_VERSION..."
  curl -L -o /tmp/apache-maven.tar.gz "https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz"
  mkdir -p "$MAVEN_DIR"
  tar -xzf /tmp/apache-maven.tar.gz -C "$MAVEN_DIR" --strip-components=1
fi
export PATH="$MAVEN_DIR/bin:$PATH"
# ensure jcodec dependency is in local repository
mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.6.0:get -Dartifact=org.jcodec:jcodec:0.2.5
