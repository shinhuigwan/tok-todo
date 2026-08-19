@echo off
setlocal

set "PROJECT_DIR=%~dp0"
set "GRADLE_CMD="

if exist "%PROJECT_DIR%gradlew.bat" set "GRADLE_CMD=%PROJECT_DIR%gradlew.bat"
if not defined GRADLE_CMD where gradle >nul 2>nul && set "GRADLE_CMD=gradle"

if not defined GRADLE_CMD (
  echo [ERROR] Gradle was not found.
  echo Open this folder in Android Studio and run Build ^> Build APK.
  echo Or push it to GitHub and run Build TokTodo test APK in Actions.
  pause
  exit /b 1
)

pushd "%PROJECT_DIR%"
call "%GRADLE_CMD%" assembleDebug
if errorlevel 1 (
  popd
  echo APK build failed.
  pause
  exit /b 1
)
popd

echo Build complete:
echo %PROJECT_DIR%app\build\outputs\apk\debug\app-debug.apk
pause
