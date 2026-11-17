# LexiTrio on the existing Namecheap hosting account

LexiTrio is already a modular Django monolith. Keep its dictionary, catalog,
curriculum, user-data and sync services within this project. Keep its SQLite
file and settings separate from UPT and Tarix Ustoz: those are separate
applications on the same account, with different schemas and migrations.

Use the cPanel Python application WSGI adapter `backend/passenger_wsgi.py`,
Python 3.12, startup `passenger_wsgi.py`, entry point `application`.
Do not publish its URL until the full database passes the deployment gate.
The GitHub seed contains only 13 dictionary entries and is not the full corpus.

## Full database handover

On the computer holding the real database, stop backend writers and create a
consistent snapshot with Python (or use the project's backup script). Do not
copy a live WAL database without its pending WAL data.

```python
import sqlite3
with sqlite3.connect('backend/db.sqlite3') as source:
    with sqlite3.connect('lexitrio-upload.sqlite3') as target:
        source.backup(target)
```

Upload the snapshot outside `public_html`, retain the original as a backup,
and set `SQLITE_PATH` to a deployment copy. Never commit the database, `.env`,
auth tokens, user progress or signing keys to GitHub. Restrict the data directory
to the account owner. Apply migrations to the copy first.

```sh
python -m pip install -r requirements.txt
python manage.py migrate --noinput
python manage.py rebuild_search_index
python manage.py verify_deployment --minimum-entries 130000
python manage.py collectstatic --noinput
python manage.py test tests apps --noinput
```

Run the index command with `manage.py help` first if the source version changes.
The database gate checks unapplied migrations, SQLite integrity, entry count and
translation presence. Failed verification must keep the app unpublished.

After the HTTPS API is live, set `LEXITRIO_API_BASE_URL` to its `/api/v1/` URL
for Android. Test health, EN/RU/UZ searches, search paging, dictionary details,
offline sync, authenticated progress and the APK against that real URL.
Redis/Celery workers and paid translation-provider keys are separate optional
infrastructure; they are not supplied by a standard Passenger application.
