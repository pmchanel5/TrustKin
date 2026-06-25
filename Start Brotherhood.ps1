$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$venvPython = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"

if (-not (Test-Path $venvPython)) {
  $pyLauncher = Get-Command py -ErrorAction SilentlyContinue
  if ($pyLauncher) {
    & py -3 -m venv .venv
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  } else {
    $pythonCmd = Get-Command python -ErrorAction SilentlyContinue
    if ($pythonCmd) {
      & python -m venv .venv
      if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } else {
      Write-Host "Python was not found. Install Python 3.11 or newer, then run this file again."
      Read-Host "Press Enter to close"
      exit 1
    }
  }
}

if (-not (Test-Path $venvPython)) {
  Write-Host "Failed to create the local Python environment."
  Read-Host "Press Enter to close"
  exit 1
}

& $venvPython -m pip install --disable-pip-version-check -r requirements.txt
if ($LASTEXITCODE -ne 0) {
  Write-Host "Failed to install Brotherhood requirements. Check your internet connection, then try again."
  Read-Host "Press Enter to close"
  exit $LASTEXITCODE
}

& $venvPython app\brotherhood.py
exit $LASTEXITCODE
