# KR1.4 - Image Upload Verification And Re-Encoding

Status: implemented.

## Summary

User-provided image data URLs are no longer stored as-is. The server validates the data URL format, decodes base64, parses the image with Pillow, rejects unsupported/script-capable formats, and re-encodes accepted images to JPEG.

## Code Map

- `app/brotherhood.py`
  - `IMAGE_DATA_URL_RE`
  - `PIL_AVAILABLE`
  - `sanitize_image_data_url()`
  - `safe_avatar()`
  - `safe_post_image()`
- `app/web/app.js`
  - `SAFE_IMAGE_RE`
  - `safeImageSrc()`
  - image preview/render paths

## Behavior

Allowed input formats:

- `data:image/jpeg;base64,...`
- `data:image/jpg;base64,...`
- `data:image/png;base64,...`
- `data:image/webp;base64,...`

Rejected:

- SVG
- charset parameters
- malformed base64
- non-image payloads
- images that Pillow cannot parse
- oversized data URLs

Stored/rendered output:

- `data:image/jpeg;base64,...`

## Verification

Command:

```powershell
py -3 -m unittest tests.test_security_mvp_alpha
```

Coverage:

- `test_image_uploads_reject_svg_and_reencode_to_jpeg`

## Future-Agent Notes

- Pillow is required for image uploads. If Pillow is missing, uploads fail closed with a clear error.
- Longer term, prefer storing generated image files/blobs and rendering server-generated safe URLs rather than keeping base64 in JSON.
