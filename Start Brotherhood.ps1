$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$python = $null
$pyLauncher = Get-Command py -ErrorAction SilentlyContinue
if ($pyLauncher) {
  & py -3 app\brotherhood.py
  exit $LASTEXITCODE
}

$pythonCmd = Get-Command python -ErrorAction SilentlyContinue
if ($pythonCmd) {
  & python app\brotherhood.py
  exit $LASTEXITCODE
}

$bundled = "C:\Users\leobe\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
if (Test-Path $bundled) {
  & $bundled app\brotherhood.py
  exit $LASTEXITCODE
}

Write-Host "Python was not found. Install Python 3.11 or newer, then run this file again."
Read-Host "Press Enter to close"
