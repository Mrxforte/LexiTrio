#!/usr/bin/env python3
"""Database Restore Utility for LexiTrio.

Restores database from a compressed backup file (.sql.gz or .db.gz).
Validates SHA-256 checksum prior to execution.
"""

import argparse
import gzip
import hashlib
import os
import shutil
import subprocess
import sys
from pathlib import Path


def verify_sha256(backup_file: Path) -> bool:
    checksum_file = backup_file.with_suffix(".sha256")
    if not checksum_file.exists():
        print("Warning: SHA-256 checksum file not found, skipping checksum verification.")
        return True

    expected = checksum_file.read_text().split()[0].strip()
    sha256_hash = hashlib.sha256()
    with open(backup_file, "rb") as f:
        for byte_block in iter(lambda: f.read(65536), b""):
            sha256_hash.update(byte_block)
    actual = sha256_hash.hexdigest()

    if actual.lower() != expected.lower():
        print(f"Error: SHA-256 checksum mismatch!\n  Expected: {expected}\n  Actual:   {actual}")
        return False
    print("SHA-256 Checksum Verified OK.")
    return True


def restore_sqlite(backup_file: Path, target_path: Path):
    print(f"Restoring SQLite database to {target_path}...")

    temp_db = target_path.with_suffix(".tmp")
    with gzip.open(backup_file, "rb") as f_in:
        with open(temp_db, "wb") as f_out:
            shutil.copyfileobj(f_in, f_out)

    if target_path.exists():
        backup_current = target_path.with_suffix(".before_restore.bak")
        shutil.copy2(target_path, backup_current)
        print(f"Backed up current database to {backup_current}")

    shutil.move(temp_db, target_path)
    print("SQLite database restored successfully.")


def restore_postgres(backup_file: Path, database_url: str):
    print(f"Restoring PostgreSQL database...")

    cmd = ["psql", "--dbname=" + database_url]

    try:
        process = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        with gzip.open(backup_file, "rb") as f_in:
            for chunk in iter(lambda: f_in.read(65536), b""):
                process.stdin.write(chunk)
        process.stdin.close()
        process.wait()

        if process.returncode != 0:
            error_msg = process.stderr.read().decode()
            raise RuntimeError(f"psql restore failed (exit code {process.returncode}): {error_msg}")
        print("PostgreSQL database restored successfully.")
    except FileNotFoundError:
        print("Error: `psql` CLI tool was not found in system PATH. Install PostgreSQL client tools or run inside Docker.")
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description="LexiTrio Database Restore Utility")
    parser.add_argument("backup_file", type=str, help="Path to backup file (.sql.gz or .db.gz)")
    parser.add_argument("--db-url", type=str, default=os.environ.get("DATABASE_URL", ""), help="Target PostgreSQL Database URL")
    parser.add_argument("--sqlite-path", type=str, default="backend/db.sqlite3", help="Target SQLite database file path")
    parser.add_argument("--force", action="store_true", help="Skip confirmation prompt")

    args = parser.parse_args()

    backup_file = Path(args.backup_file)
    if not backup_file.exists():
        print(f"Error: Backup file not found at {backup_file}")
        sys.exit(1)

    if not verify_sha256(backup_file):
        sys.exit(1)

    if not args.force:
        confirm = input(f"WARNING: This operation will overwrite target database data with {backup_file}. Proceed? (y/N): ")
        if confirm.strip().lower() not in {"y", "yes"}:
            print("Restore operation cancelled.")
            sys.exit(0)

    db_url = args.db_url or os.environ.get("DATABASE_URL", "")

    if db_url and db_url.startswith(("postgres", "postgresql")):
        restore_postgres(backup_file, db_url)
    else:
        restore_sqlite(backup_file, Path(args.sqlite_path))


if __name__ == "__main__":
    main()
