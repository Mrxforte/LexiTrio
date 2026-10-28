from django.apps import AppConfig


class MaintenanceConfig(AppConfig):
    """Operational management commands that span every app (e.g. SQLite -> PostgreSQL copy)."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.maintenance"
    label = "maintenance"
    verbose_name = "Maintenance"
