import django.utils.timezone
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("learning", "0004_remove_savedword_learning_sa_user_id_22298f_idx_and_more"),
    ]

    operations = [
        migrations.AddField(
            model_name="dictionaryentry",
            name="updated_at",
            field=models.DateTimeField(
                auto_now=True, db_index=True, default=django.utils.timezone.now
            ),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name="wordmeaning",
            name="updated_at",
            field=models.DateTimeField(
                auto_now=True, db_index=True, default=django.utils.timezone.now
            ),
            preserve_default=False,
        ),
    ]
