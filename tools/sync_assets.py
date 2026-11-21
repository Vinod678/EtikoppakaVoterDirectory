"""Copy a ward's prepared dataset and photos into the Android app's assets folder.

Usage (from the project root):
    python tools/sync_assets.py                 # Ward 11 (original dataset)
    python tools/sync_assets.py 2               # Ward 2 -> assets/wards/voters_ward_2.json + assets/voter_photos/ward_2/
    python tools/sync_assets.py 2 --no-optimize # exact byte copies of every photo (no compression at all)

The dataset JSON is copied byte for byte. Each photo is re-encoded as a JPEG (quality JPEG_QUALITY,
longest side at most MAX_SIDE pixels, proportional) when that makes it at least MIN_SAVING smaller;
otherwise it is copied byte for byte. The file keeps its name - even a ".png" - because the app
decodes photos by their content, so the voter -> photo mapping, the JSON and the database never
change. 480 px is still twice a typical voter photo (240 x 320) and sharp in the app's largest
photo view; most original photos are 320 px or less and keep their size.

The originals in output/ are never modified (they remain the source of truth); every resized file
is listed in <ward output folder>/asset_photo_optimization.csv with both checksums.
Files in the ward's assets photo folder that no longer exist in its output folder are removed
(only files directly in that folder - other wards' sub-folders are never touched).
The copy is verified with SHA-256 afterwards.
"""
import csv
import hashlib
import io
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ward_config

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools', '.pylib'))  # optional private library folder (see README)
ARGS = [a for a in sys.argv[1:] if not a.startswith('--')]
OPTIMIZE = '--no-optimize' not in sys.argv
MAX_SIDE = 480
JPEG_QUALITY = 85
MIN_SAVING = 0.10  # re-encode only when it saves at least 10 %
W = ward_config.ward(int(ARGS[0]) if ARGS else 11)
SRC_JSON = os.path.join(ROOT, W['json'])
SRC_PHOTOS = os.path.join(ROOT, W['photos'])
ASSETS = os.path.join(ROOT, 'app', 'src', 'main', 'assets')
DST_JSON = os.path.join(ASSETS, W['asset_json'])
DST_PHOTOS = os.path.join(ASSETS, W['asset_photos'])
OPT_LOG = os.path.join(os.path.dirname(os.path.join(ROOT, W['manifest'])), 'asset_photo_optimization.csv')


def sha_bytes(b):
    return hashlib.sha256(b).hexdigest()


def sha(path):
    with open(path, 'rb') as f:
        return sha_bytes(f.read())


def optimized(src):
    """Compressed JPEG bytes for a photo, or None when the original is copied unchanged."""
    from PIL import Image
    im = Image.open(src)
    w, h = im.size
    im.load()
    small = im
    if max(w, h) > MAX_SIDE:
        k = MAX_SIDE / max(w, h)
        small = im.resize((max(1, round(w * k)), max(1, round(h * k))), Image.LANCZOS)
    out = io.BytesIO()
    small.convert('RGB').save(out, 'JPEG', quality=JPEG_QUALITY, optimize=True, progressive=True)
    data = out.getvalue()
    return (data, (w, h), small.size) if len(data) <= os.path.getsize(src) * (1 - MIN_SAVING) else None


def main():
    for p in (SRC_JSON, SRC_PHOTOS):
        if not os.path.exists(p):
            sys.exit('Missing %s - run the dataset tools first (see tools/README.md).' % p)
    os.makedirs(DST_PHOTOS, exist_ok=True)
    os.makedirs(os.path.dirname(DST_JSON), exist_ok=True)
    shutil.copyfile(SRC_JSON, DST_JSON)
    src_files = sorted(os.listdir(SRC_PHOTOS))
    for name in os.listdir(DST_PHOTOS):
        if name not in src_files and os.path.isfile(os.path.join(DST_PHOTOS, name)):
            os.remove(os.path.join(DST_PHOTOS, name))
    log, expected = [], {}
    for name in src_files:
        src, dst = os.path.join(SRC_PHOTOS, name), os.path.join(DST_PHOTOS, name)
        res = optimized(src) if OPTIMIZE else None
        if res is None:
            shutil.copyfile(src, dst)
            expected[name] = sha(src)
        else:
            data, (w, h), (nw, nh) = res
            with open(dst, 'wb') as f:
                f.write(data)
            expected[name] = sha_bytes(data)
            log.append([name, w, h, os.path.getsize(src), sha(src), nw, nh, len(data), expected[name]])
    with open(OPT_LOG, 'w', newline='', encoding='utf-8') as f:
        wr = csv.writer(f)
        wr.writerow(['photo_filename', 'original_width_px', 'original_height_px', 'original_bytes', 'original_sha256',
                     'asset_width_px', 'asset_height_px', 'asset_bytes', 'asset_sha256'])
        wr.writerows(log)
    bad = [n for n in src_files if sha(os.path.join(DST_PHOTOS, n)) != expected[n]]
    if sha(SRC_JSON) != sha(DST_JSON):
        bad.append(os.path.basename(DST_JSON))
    if bad:
        sys.exit('Copy verification FAILED for: %s' % bad)
    saved = sum(r[3] - r[7] for r in log)
    print('Copied %s and %d photos to %s (verified); %d photo(s) compressed (JPEG, max %d px), %.1f MB saved; log: %s'
          % (W['asset_json'], len(src_files), DST_PHOTOS, len(log), MAX_SIDE, saved / 1e6, os.path.relpath(OPT_LOG, ROOT)))


if __name__ == '__main__':
    main()
