@echo off
setlocal
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
    echo Java compiler not found. Install JDK 17 or newer and add its bin folder to PATH.
    pause
    exit /b 1
)
if not exist build mkdir build
javac --release 17 -encoding UTF-8 -cp "lib/*" -d build src\quiz\*.java
if errorlevel 1 (
    pause
    exit /b 1
)
jar --create --file QuizPlatform.jar --main-class quiz.Main -C build quiz
if errorlevel 1 (
    pause
    exit /b 1
)
echo Build successful. Open run.bat to launch the app.
pause
