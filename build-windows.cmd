@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle 8.9 or newer is required. You can also build the installer from GitHub Actions.
  exit /b 1
)
gradle --no-daemon :desktop:clean :desktop:check :desktop:windowsInstaller
if errorlevel 1 exit /b 1
echo Installer: desktop\build\installer\TokTodo-1.0.0.exe
endlocal
