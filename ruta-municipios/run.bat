@echo off
REM Compila y ejecuta SIN Maven: solo necesita el JDK 21.
setlocal enabledelayedexpansion
chcp 65001 >nul
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
if exist sources.txt del sources.txt
for /r src\main\java %%f in (*.java) do (
  set "p=%%f"
  echo "!p:\=/!">>sources.txt
)
javac --release 21 -encoding UTF-8 -d out @sources.txt
if errorlevel 1 (del sources.txt & pause & exit /b 1)
del sources.txt
xcopy /e /i /q /y src\main\resources out >nul
java -Dstdout.encoding=UTF-8 -cp out co.rutamunicipios.infrastructure.config.Application
pause
