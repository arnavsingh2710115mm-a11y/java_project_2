@echo off
cd /d "%~dp0"
if not exist build-web mkdir build-web
javac --release 17 -encoding UTF-8 -cp "lib/*" -d build-web src/quiz/*.java
if errorlevel 1 (
  echo Install Java JDK 17 or newer to compile this web app.
  pause
  exit /b 1
)
echo Open http://localhost:8080 in your browser.
echo On first launch, use the setup key printed below to create the administrator.
java -cp "build-web;lib/*" quiz.WebServer
pause
