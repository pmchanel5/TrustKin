# Profile Edit UI

Status: implemented.

## Summary

Users can now edit their local Brotherhood profile after initial setup. The profile card exposes an inline editor for nickname and profile image on both the setup screen and the dashboard sidebar.

This is tracked as non-OKR progress because the current MVP Alpha OKR has privacy and connection goals, but no direct KR for profile editing or identity-management polish.

## Code Map

- `app/web/app.js`
  - `profileEditorOpen`
  - `profileAvatarDraft`
  - `renderProfilePanel()`
  - `wireProfileEditor()`
  - setup profile card calls `renderProfilePanel()`
  - dashboard sidebar calls `renderProfilePanel()`
- `app/web/styles.css`
  - `.profile-edit-button`
  - `.profile-editor`
- `app/brotherhood.py`
  - existing `/api/profile` handler is reused
  - existing `safe_avatar()` validation still sanitizes saved profile images

## Behavior

Profile card:

- shows current avatar or initials
- shows current nickname
- includes an `Edit` button

Profile editor:

- edits nickname
- selects a replacement profile image
- previews the selected image
- removes the current picture
- cancels without saving
- saves through `/api/profile`

Saving refreshes `/api/bootstrap` and the current circle state so the new nickname/avatar appears in the local profile card and circle views.

The editor does not create a new `user_id`; it updates the existing local profile.

## Verification

Commands:

```powershell
node --check app\web\app.js
py -3 -m unittest tests.test_security_mvp_alpha
```

Live check:

- In-app browser showed `Edit` on the setup profile card.
- Opening `Edit` showed profile image, nickname, remove picture, cancel, and save controls.
- Dashboard sidebar also showed `Edit` after entering host mode.

## Future-Agent Notes

- Keep profile editing separate from Host/Join connection actions. Connection switching should not silently overwrite nickname or avatar.
- If profile deletion/reset is added later, map it to Objective 3 / KR3.3 rather than this non-OKR note.
- If stronger identity controls are added, add regression coverage that `user_id` remains stable when only nickname or avatar changes.
