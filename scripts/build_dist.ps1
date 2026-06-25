$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Invoke-Checked {
  param(
    [Parameter(Mandatory = $true)]
    [string] $Command,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $Arguments
  )

  & $Command @Arguments
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }
}

$venvPython = Join-Path $root ".venv\Scripts\python.exe"
if (-not (Test-Path $venvPython)) {
  $pyLauncher = Get-Command py -ErrorAction SilentlyContinue
  if ($pyLauncher) {
    Invoke-Checked "py" "-3" "-m" "venv" ".venv"
  } else {
    $pythonCmd = Get-Command python -ErrorAction SilentlyContinue
    if (-not $pythonCmd) {
      throw "Python was not found. Install Python 3.11 or newer, then run this script again."
    }
    Invoke-Checked "python" "-m" "venv" ".venv"
  }
}

if (-not (Test-Path $venvPython)) {
  throw "Failed to create the local Python environment."
}

Invoke-Checked $venvPython "-m" "pip" "install" "--upgrade" "pip"
Invoke-Checked $venvPython "-m" "pip" "install" "-r" "requirements-build.txt"

$distPath = Join-Path $root "dist"
$buildPath = Join-Path $root "build"
$specPath = Join-Path $root "Brotherhood.spec"

foreach ($path in @($distPath, $buildPath, $specPath)) {
  if (Test-Path $path) {
    $resolved = Resolve-Path $path
    if (-not $resolved.Path.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
      throw "Refusing to remove path outside project root: $($resolved.Path)"
    }
    Remove-Item -LiteralPath $resolved.Path -Recurse -Force
  }
}

Invoke-Checked $venvPython `
  "-m" `
  "PyInstaller" `
  "--noconfirm" `
  "--clean" `
  "--name" "Brotherhood" `
  "--add-data" "app\web;web" `
  "--add-data" "app\bin;bin" `
  "--collect-submodules" "PIL" `
  "--collect-data" "PIL" `
  "app\brotherhood.py"

$exe = Join-Path $root "dist\Brotherhood\Brotherhood.exe"
if (-not (Test-Path $exe)) {
  throw "Build finished, but $exe was not created."
}

Write-Host "Built $exe"
