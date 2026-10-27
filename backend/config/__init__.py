import copy
import sys

# Workaround for Django 5.x on Python 3.14 where Context.__copy__ and RenderContext.__copy__ raise AttributeError
if sys.version_info >= (3, 14):
    try:
        from django.template.context import Context, RenderContext

        def _fixed_render_context_copy(self):
            obj = RenderContext.__new__(RenderContext)
            obj.dicts = [d.copy() if hasattr(d, "copy") else d for d in self.dicts]
            return obj

        RenderContext.__copy__ = _fixed_render_context_copy

        def _fixed_context_copy(self):
            obj = Context.__new__(Context)
            obj.dicts = [d.copy() if hasattr(d, "copy") else d for d in self.dicts]
            obj.autoescape = getattr(self, "autoescape", True)
            obj.use_l10n = getattr(self, "use_l10n", None)
            obj.use_tz = getattr(self, "use_tz", None)
            obj.template_name = getattr(self, "template_name", None)
            obj.render_context = _fixed_render_context_copy(getattr(self, "render_context", RenderContext()))
            return obj

        Context.__copy__ = _fixed_context_copy
    except Exception:
        pass

# This will make sure the app is always imported when
# Django starts so that shared_task will use this app.
try:
    from .celery import app as celery_app
    __all__ = ("celery_app",)
except ImportError:
    pass
