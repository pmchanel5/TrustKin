# KR4.6 - Start Scripts Contain No Machine-Specific Paths

Status: implemented.

## Summary

The Windows launchers no longer reference a developer-specific Codex runtime path. They create and use a project-local `.venv` instead.

## Code Map

- `Start Brotherhood.bat`
  - creates `.venv` with `py -3 -m venv .venv` or `python -m venv .venv`
  - installs pinned runtime dependencies from `requirements.txt`
  - runs `app\brotherhood.py` using `.venv\Scripts\python.exe`
- `Start Brotherhood.ps1`
  - mirrors the same local `.venv` behavior for PowerShell launches
- `requirements.txt`
  - pins runtime dependencies, currently `Pillow`

## Behavior

Users still need Python installed to run from source, but they no longer need dependencies preinstalled globally. The launchers install missing requirements into the repo-local virtual environment.

## Verification

Check scripts with:

```powershell
rg -n "C:\\Users|codex-runtimes|leobe" "Start Brotherhood.bat" "Start Brotherhood.ps1"
```

Expected result: no matches.

## Future-Agent Notes

- Keep `.venv/` ignored; it is generated local state.
- Add new runtime dependencies to `requirements.txt`.
- Avoid reintroducing absolute machine-specific fallback paths.
