@echo off
setlocal
cd /d "%~dp0"
if not exist test-build mkdir test-build
javac --release 17 -encoding UTF-8 -cp "lib/*" -d test-build src\quiz\*.java test\quiz\ProjectTest.java
if errorlevel 1 (
    pause
    exit /b 1
)
java -cp "test-build;lib/*" quiz.ProjectTest
pause
