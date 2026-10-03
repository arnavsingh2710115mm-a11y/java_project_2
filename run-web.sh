#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build-web
javac --release 17 -encoding UTF-8 -cp 'lib/*' -d build-web src/quiz/*.java
exec java -cp 'build-web:lib/*' quiz.WebServer
