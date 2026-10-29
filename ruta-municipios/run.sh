#!/usr/bin/env sh
# Compila y ejecuta SIN Maven: solo necesita el JDK 21.
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac --release 21 -encoding UTF-8 -d out $(find src/main/java -name "*.java")
cp -r src/main/resources/. out/
exec java -Dstdout.encoding=UTF-8 -cp out co.rutamunicipios.infrastructure.config.Application
