"""Text normalisation shared by imports, search and generated entries.

One definition of "the same word": Unicode compatibility form (NFKC), lower
case, typographic apostrophe/dash folded to ASCII, whitespace collapsed. Headwords
are stored in this form and queries are reduced to it, so both always meet.
"""

import unicodedata


def normalize_text(value) -> str:
    text = unicodedata.normalize("NFKC", str(value or ""))
    text = text.strip().lower().replace("\u2019", "'").replace("\u2013", "-")
    return " ".join(text.split())
