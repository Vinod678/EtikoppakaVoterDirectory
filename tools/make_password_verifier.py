"""Creates a password verifier for app/src/main/java/.../auth/PasswordPolicy.kt.

The app never contains a password, only a random salt and a PBKDF2-HMAC-SHA256 hash
(20,000 iterations, 32 bytes) of the fixed part of each password ("base"):

  - adminBase / viewerBase: the part before the day-of-month digits (until 2 Dec 2026).
  - viewerFixed: the complete Viewer password used from 3 Dec 2026.
  - adminFixed: the complete, separate Admin password used from 3 Dec 2026. While it is null in
    PasswordPolicy.kt, Admin sign-in is unavailable from 3 Dec 2026.

Usage (the password is typed without echo and never written to disk):

    python tools/make_password_verifier.py            # asks for the password
    python tools/make_password_verifier.py --stdin    # reads one line from standard input

Paste the printed salt and hash into PasswordPolicy.kt and rebuild the app.
"""
import getpass
import hashlib
import os
import sys

ITERATIONS = 20_000


def main() -> None:
    if "--stdin" in sys.argv:
        password = sys.stdin.readline().rstrip("\r\n")
    else:
        password = getpass.getpass("Password (fixed part): ")
    if not password:
        sys.exit("Empty password.")
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, ITERATIONS, 32)
    print(f'salt = "{salt.hex()}", hash = "{digest.hex()}"')


if __name__ == "__main__":
    main()
