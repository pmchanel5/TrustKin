# KR4.1 - `dist/` Remains Ignored And Untracked

Status: repository-state-complete; ongoing policy.

## Summary

The repository currently ignores release-like generated output under `dist/`, and no `dist/` files are tracked by git.

## Code Map

- `.gitignore`
  - `dist/`
  - `build/`
  - `*.spec`

## Verification

Commands:

```powershell
Get-Content .gitignore
git ls-files | Select-String '^dist/'
```

Observed state:

- `.gitignore` includes `dist/`
- `git ls-files | Select-String '^dist/'` returns no tracked files

## Future-Agent Notes

- Do not commit packaged app output.
- Use workflow artifacts for per-commit builds.
- Use GitHub Release assets for tester/release packages.
- The local `dist/` folder may exist on disk and be stale; it is not the source of truth.
