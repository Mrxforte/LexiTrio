from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("learning", "0011_contentbatch_curriculumcourse_curriculumtopic_and_more"),
    ]

    operations = [
        migrations.CreateModel(
            name="DictionaryTombstone",
            fields=[
                (
                    "id",
                    models.BigAutoField(
                        auto_created=True,
                        primary_key=True,
                        serialize=False,
                        verbose_name="ID",
                    ),
                ),
                ("entry_id", models.PositiveBigIntegerField(db_index=True)),
                ("deleted_at", models.DateTimeField(auto_now_add=True, db_index=True)),
            ],
        ),
    ]
