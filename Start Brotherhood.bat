@echo off
setlocal
cd /d "%~dp0"

set VENV_PY=.venv\Scripts\python.exe
if not exist "%VENV_PY%" (
  where py >nul 2>nul
  if not errorlevel 1 (
    py -3 -m venv .venv
  ) else (
    where python >nul 2>nul
    if not errorlevel 1 (
      python -m venv .venv
    ) else (
      echo Python was not found. Install Python 3.11 or newer, then run this file again.
      pause
      exit /b 1
    )
  )
)

if not exist "%VENV_PY%" (
  echo Failed to create the local Python environment.
  pause
  exit /b 1
)

"%VENV_PY%" -m pip install --disable-pip-version-check -r requirements.txt
if errorlevel 1 (
  echo Failed to install Brotherhood requirements. Check your internet connection, then try again.
  pause
  exit /b 1
)

"%VENV_PY%" app\brotherhood.py
exit /b %errorlevel%
