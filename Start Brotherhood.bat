@echo off
setlocal
cd /d "%~dp0"

where py >nul 2>nul
if %errorlevel%==0 (
  py -3 app\brotherhood.py
  exit /b %errorlevel%
)

where python >nul 2>nul
if %errorlevel%==0 (
  python app\brotherhood.py
  exit /b %errorlevel%
)

set BUNDLED_PY=C:\Users\leobe\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe
if exist "%BUNDLED_PY%" (
  "%BUNDLED_PY%" app\brotherhood.py
  exit /b %errorlevel%
)

echo Python was not found. Install Python 3.11 or newer, then run this file again.
pause
