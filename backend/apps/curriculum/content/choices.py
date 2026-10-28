"""Stored values of the curriculum (persisted and consumed by the Android client: never change them)."""

from django.db import models


class PublicationStatus(models.TextChoices):
    """Editorial life cycle shared by lessons, exercises and test questions."""

    DRAFT = "draft", "Draft"
    REVIEW = "review", "Review"
    APPROVED = "approved", "Approved"
    PUBLISHED = "published", "Published"
    REJECTED = "rejected", "Rejected"


class ValidationStatus(models.TextChoices):
    """Outcome of the automated content checks."""

    VALID = "valid", "Valid"
    INVALID = "invalid", "Invalid"


class GrammarTableType(models.TextChoices):
    RULE = "RULE", "Rule"
    WORD_FORM = "WORD_FORM", "Word Form"
    VERB_CONJUGATION = "VERB_CONJUGATION", "Verb Conjugation"
    TENSE_COMPARISON = "TENSE_COMPARISON", "Tense Comparison"
    SENTENCE_STRUCTURE = "SENTENCE_STRUCTURE", "Sentence Structure"


class ExerciseType(models.TextChoices):
    MULTIPLE_CHOICE = "MULTIPLE_CHOICE", "Multiple Choice"
    FILL_IN_BLANK = "FILL_IN_BLANK", "Fill in Blank"
    TYPE_MISSING_WORD = "TYPE_MISSING_WORD", "Type Missing Word"
    SENTENCE_ORDERING = "SENTENCE_ORDERING", "Sentence Ordering"
    WORD_MATCHING = "WORD_MATCHING", "Word Matching"
    ERROR_IDENTIFICATION = "ERROR_IDENTIFICATION", "Error Identification"
    ERROR_CORRECTION = "ERROR_CORRECTION", "Error Correction"
    TRANSLATION = "TRANSLATION", "Translation"
    AFFIRMATIVE_TO_NEGATIVE = "AFFIRMATIVE_TO_NEGATIVE", "Affirmative to Negative"
    SENTENCE_TO_QUESTION = "SENTENCE_TO_QUESTION", "Sentence to Question"
    CONJUGATION = "CONJUGATION", "Conjugation"
    SHORT_ANSWER = "SHORT_ANSWER", "Short Answer"
    LISTENING = "LISTENING", "Listening"
    CONTEXTUAL_VOCAB = "CONTEXTUAL_VOCAB", "Contextual Vocab"
    MIXED_REVIEW = "MIXED_REVIEW", "Mixed Review"


class ContentBatchStatus(models.TextChoices):
    INIT = "init", "Initialized"
    IN_PROGRESS = "in_progress", "In Progress"
    COMPLETED = "completed", "Completed"
    FAILED = "failed", "Failed"
