@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
    echo Java was not found. Install JDK 17 or newer, then reopen this file.
    pause
    exit /b 1
)
java -cp "QuizPlatform.jar;lib/*" quiz.Main
if errorlevel 1 (
    echo.
    echo The app could not start. Java 17 or newer is required.
    echo Keep this window open and share the error shown above.
    pause
)
