#!/usr/bin/env python3
"""Database Backup Utility for LexiTrio.

Supports both PostgreSQL (pg_dump) and SQLite databases.
Creates timestamped, compressed backup files with SHA-256 checksums.
"""

import argparse
import gzip
import hashlib
import os
import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path


def compute_sha256(file_path: Path) -> str:
    sha256_hash = hashlib.sha256()
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(65536), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()


def backup_sqlite(db_path: Path, output_dir: Path) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_file = output_dir / f"lexitrio_sqlite_backup_{timestamp}.db.gz"

    print(f"Creating SQLite backup from {db_path}...")
    temp_backup = output_dir / f"temp_{timestamp}.db"

    # SQLite online backup command or copy
    try:
        import sqlite3
        src_conn = sqlite3.connect(str(db_path))
        dst_conn = sqlite3.connect(str(temp_backup))
        with dst_conn:
            src_conn.backup(dst_conn)
        dst_conn.close()
        src_conn.close()
    except Exception:
        shutil.copy2(db_path, temp_backup)

    with open(temp_backup, "rb") as f_in:
        with gzip.open(backup_file, "wb") as f_out:
            shutil.copyfileobj(f_in, f_out)

    if temp_backup.exists():
        temp_backup.unlink()

    return backup_file


def backup_postgres(database_url: str, output_dir: Path) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_file = output_dir / f"lexitrio_postgres_backup_{timestamp}.sql.gz"

    print(f"Creating PostgreSQL backup using pg_dump...")

    cmd = ["pg_dump", "--dbname=" + database_url, "--clean", "--if-exists", "--no-owner"]

    try:
        process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        with gzip.open(backup_file, "wb") as f_out:
            for chunk in iter(lambda: process.stdout.read(65536), b""):
                f_out.write(chunk)
        process.wait()
        if process.returncode != 0:
            error_msg = process.stderr.read().decode()
            raise RuntimeError(f"pg_dump failed (exit code {process.returncode}): {error_msg}")
    except FileNotFoundError:
        print("Error: `pg_dump` CLI tool was not found in system PATH. Install PostgreSQL client tools or run inside Docker.")
        sys.exit(1)

    return backup_file


def main():
    parser = argparse.ArgumentParser(description="LexiTrio Database Backup Utility")
    parser.add_argument("--output-dir", type=str, default="backups", help="Directory to store backup files.")
    parser.add_argument("--db-url", type=str, default=os.environ.get("DATABASE_URL", ""), help="Database URL")
    parser.add_argument("--sqlite-path", type=str, default="backend/db.sqlite3", help="Path to SQLite database file")

    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    db_url = args.db_url or os.environ.get("DATABASE_URL", "")

    started = time.monotonic()
    if db_url and db_url.startswith(("postgres", "postgresql")):
        backup_path = backup_postgres(db_url, output_dir)
    else:
        sqlite_path = Path(args.sqlite_path)
        if not sqlite_path.exists():
            print(f"Error: SQLite database not found at {sqlite_path}")
            sys.exit(1)
        backup_path = backup_sqlite(sqlite_path, output_dir)

    checksum = compute_sha256(backup_path)
    file_size_mb = round(backup_path.stat().st_size / (1024 * 1024), 2)
    duration = round(time.monotonic() - started, 2)

    # Save metadata file
    checksum_file = backup_path.with_suffix(".sha256")
    checksum_file.write_text(f"{checksum}  {backup_path.name}\n")

    print("\n--- BACKUP COMPLETED SUCCESSFULLY ---")
    print(f"File: {backup_path.resolve()}")
    print(f"Size: {file_size_mb} MB")
    print(f"SHA-256: {checksum}")
    print(f"Duration: {duration} seconds")


if __name__ == "__main__":
    main()
