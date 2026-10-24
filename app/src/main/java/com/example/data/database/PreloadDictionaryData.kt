package com.example.data.database

import com.example.data.model.CollocationEntity
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.ExampleSentenceEntity
import com.example.data.model.Language
import com.example.data.model.SynonymAntonymEntity
import com.example.data.model.TranslationEntity
import com.example.data.model.WordFormEntity
import com.example.data.model.WordMeaningEntity

object PreloadDictionaryData {

    data class PreloadBundle(
        val entries: List<DictionaryEntryEntity>,
        val meanings: List<WordMeaningEntity>,
        val translations: List<TranslationEntity>,
        val examples: List<ExampleSentenceEntity>,
        val synonyms: List<SynonymAntonymEntity>,
        val forms: List<WordFormEntity>,
        val collocations: List<CollocationEntity>
    )

    fun getInitialData(): PreloadBundle {
        val entries = mutableListOf<DictionaryEntryEntity>()
        val meanings = mutableListOf<WordMeaningEntity>()
        val translations = mutableListOf<TranslationEntity>()
        val examples = mutableListOf<ExampleSentenceEntity>()
        val synonyms = mutableListOf<SynonymAntonymEntity>()
        val forms = mutableListOf<WordFormEntity>()
        val collocations = mutableListOf<CollocationEntity>()

        var entryId = 1L
        var meaningId = 1L
        var transId = 1L
        var exampleId = 1L
        var synId = 1L
        var formId = 1L
        var collocId = 1L

        fun addEntry(
            headword: String,
            language: String,
            ipa: String,
            pos: String,
            cefr: String,
            rank: Int,
            primaryEn: String,
            primaryRu: String,
            primaryUz: String,
            senses: List<SenseSpec>,
            formsList: List<Pair<String, String>> = emptyList(),
            collocsList: List<Triple<String, String, String>> = emptyList()
        ) {
            val currEntryId = entryId++
            val norm = Language.normalizeText(headword)
            entries.add(
                DictionaryEntryEntity(
                    id = currEntryId,
                    headword = headword,
                    headwordNormalized = norm,
                    language = language,
                    phoneticIpa = ipa,
                    partOfSpeech = pos,
                    cefrLevel = cefr,
                    frequencyRank = rank,
                    lemma = headword,
                    alternativeSpellings = if (language == "uz") Language.transliterateUzbek(headword, toCyrillic = true) else "",
                    primaryMeaningEn = primaryEn,
                    primaryMeaningRu = primaryRu,
                    primaryMeaningUz = primaryUz
                )
            )

            formsList.forEach { (type, value) ->
                forms.add(
                    WordFormEntity(
                        id = formId++,
                        entryId = currEntryId,
                        formType = type,
                        formValue = value
                    )
                )
            }

            collocsList.forEach { (expr, ru, uz) ->
                collocations.add(
                    CollocationEntity(
                        id = collocId++,
                        entryId = currEntryId,
                        expression = expr,
                        translationRu = ru,
                        translationUz = uz
                    )
                )
            }

            senses.forEachIndexed { idx, sense ->
                val currMeaningId = meaningId++
                meanings.add(
                    WordMeaningEntity(
                        id = currMeaningId,
                        entryId = currEntryId,
                        senseNumber = idx + 1,
                        definitionEn = sense.defEn,
                        definitionRu = sense.defRu,
                        definitionUz = sense.defUz,
                        register = sense.register,
                        grammarNotes = sense.grammarNotes
                    )
                )

                sense.translations.forEach { (lang, text, note) ->
                    translations.add(
                        TranslationEntity(
                            id = transId++,
                            entryId = currEntryId,
                            meaningId = currMeaningId,
                            targetLanguage = lang,
                            translationText = text,
                            contextNote = note
                        )
                    )
                }

                sense.examples.forEach { (en, ru, uz) ->
                    examples.add(
                        ExampleSentenceEntity(
                            id = exampleId++,
                            entryId = currEntryId,
                            meaningId = currMeaningId,
                            sentenceEn = en,
                            sentenceRu = ru,
                            sentenceUz = uz
                        )
                    )
                }

                sense.synonyms.forEach { syn ->
                    synonyms.add(
                        SynonymAntonymEntity(
                            id = synId++,
                            meaningId = currMeaningId,
                            word = syn,
                            isAntonym = false
                        )
                    )
                }

                sense.antonyms.forEach { ant ->
                    synonyms.add(
                        SynonymAntonymEntity(
                            id = synId++,
                            meaningId = currMeaningId,
                            word = ant,
                            isAntonym = true
                        )
                    )
                }
            }
        }

        // --- CORE VOCABULARY CORPUS ---

        // 1. acquire (verb, B2)
        addEntry(
            headword = "acquire",
            language = "en",
            ipa = "/əˈkwaɪər/",
            pos = "verb",
            cefr = "B2",
            rank = 950,
            primaryEn = "to buy or obtain an asset or object; to gain knowledge or skill",
            primaryRu = "приобретать, получать, овладевать",
            primaryUz = "egallamoq, qo‘lga kiritmoq, o‘zlashtirmoq",
            formsList = listOf("Past" to "acquired", "Past Participle" to "acquired", "3rd Person" to "acquires", "-ing" to "acquiring"),
            collocsList = listOf(
                Triple("acquire knowledge", "приобретать знания", "bilim egallamoq"),
                Triple("acquire a taste for", "полюбить со временем", "vaqt o‘tib yoqtirib qolmoq")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "To gain or obtain something through effort, experience, or purchase.",
                    defRu = "Получить или обрести что-либо благодаря усилиям, опыту или покупке.",
                    defUz = "Harakat, tajriba yoki xarid orqali biror narsaga ega bo‘lmoq.",
                    register = "formal",
                    grammarNotes = "Transitive verb [T]",
                    translations = listOf(
                        Triple("ru", "приобретать", "о навыках, имуществе"),
                        Triple("ru", "овладевать", "о знании языка"),
                        Triple("uz", "qo‘lga kiritmoq", "umumnatijada"),
                        Triple("uz", "o‘zlashtirmoq", "bilim va ko‘nikma haqida")
                    ),
                    examples = listOf(
                        Triple(
                            "She has acquired a good working knowledge of three languages.",
                            "Она приобрела хорошее практическое знание трех языков.",
                            "U uchta tilni yaxshi amaliy darajada o‘zlashtirdi."
                        ),
                        Triple(
                            "The museum has recently acquired several rare manuscripts.",
                            "Музей недавно приобрел несколько редких рукописей.",
                            "Muzey yaqinda bir nechta noyob qo‘lyozmalarni sotib oldi."
                        )
                    ),
                    synonyms = listOf("obtain", "gain", "attain", "procure"),
                    antonyms = listOf("lose", "relinquish", "forfeit")
                )
            )
        )

        // 2. resilience (noun, B2)
        addEntry(
            headword = "resilience",
            language = "en",
            ipa = "/rɪˈzɪliəns/",
            pos = "noun",
            cefr = "B2",
            rank = 880,
            primaryEn = "the capacity to withstand or recover quickly from difficulties",
            primaryRu = "стойкость, устойчивость, жизнестойкость",
            primaryUz = "bardoshlilik, chidamlilik, matonat",
            formsList = listOf("Adjective" to "resilient", "Adverb" to "resiliently"),
            collocsList = listOf(
                Triple("show remarkable resilience", "проявлять удивительную стойкость", "hayratlanarli darajada matonat ko‘rsatmoq"),
                Triple("economic resilience", "экономическая устойчивость", "iqtisodiy barqarorlik")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "The ability of people or things to recover quickly after something unpleasant, such as shock or injury.",
                    defRu = "Способность быстро восстанавливать силы после потрясений, трудностей или травм.",
                    defUz = "Qiyinchilik, sinov yoki jarohatlardan so‘ng tezda o‘zini tiklay olish qobiliyati.",
                    register = "general",
                    grammarNotes = "Uncountable noun [U]",
                    translations = listOf(
                        Triple("ru", "жизнестойкость", "о характере"),
                        Triple("ru", "упругость", "о материалах"),
                        Triple("uz", "matonat", "qiyinchiliklarga qarshi"),
                        Triple("uz", "egiluvchanlik", "fizik xususiyat")
                    ),
                    examples = listOf(
                        Triple(
                            "Courage and resilience helped the community rebuild after the disaster.",
                            "Мужество и стойкость помогли обществу восстановиться после катастрофы.",
                            "Jasorat va matonat jamiyatga ofatdan so‘ng qayta tiklanishga yordam berdi."
                        )
                    ),
                    synonyms = listOf("toughness", "endurance", "fortitude", "adaptability"),
                    antonyms = listOf("vulnerability", "fragility", "weakness")
                )
            )
        )

        // 3. eloquent (adjective, C1)
        addEntry(
            headword = "eloquent",
            language = "en",
            ipa = "/ˈeləkwənt/",
            pos = "adjective",
            cefr = "C1",
            rank = 720,
            primaryEn = "fluent or persuasive in speaking or writing",
            primaryRu = "красноречивый, выразительный, убедительный",
            primaryUz = "notiqlik qobiliyatiga ega, fasih, chechan",
            formsList = listOf("Adverb" to "eloquently", "Noun" to "eloquence"),
            collocsList = listOf(
                Triple("an eloquent speech", "красноречивая речь", "fasih nutq"),
                Triple("eloquent silence", "выразительное молчание", "ma’noli sukut")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "Giving a clear, strong message; able to express ideas well in speech or writing.",
                    defRu = "Умеющий четко и красиво излагать мысли в устной или письменной форме.",
                    defUz = "O‘z fikrlarini og‘zaki yoki yozma ravishda chiroyli va ta’sirchan bayon qila oladigan.",
                    register = "formal",
                    grammarNotes = "Descriptive adjective",
                    translations = listOf(
                        Triple("ru", "красноречивый", "о человеке, тексте"),
                        Triple("ru", "выразительный", "о взгляде, молчании"),
                        Triple("uz", "fasih", "go‘zal nutq sohibi"),
                        Triple("uz", "chechan", "so‘zga usta")
                    ),
                    examples = listOf(
                        Triple(
                            "He delivered an eloquent defense of human rights.",
                            "Он произнес красноречивую речь в защиту прав человека.",
                            "U inson huquqlarini himoya qilib, nihoyatda fasih nutq so‘zladi."
                        )
                    ),
                    synonyms = listOf("articulate", "persuasive", "expressive", "fluent"),
                    antonyms = listOf("inarticulate", "hesitant", "unconvincing")
                )
            )
        )

        // 4. comprehend (verb, B2)
        addEntry(
            headword = "comprehend",
            language = "en",
            ipa = "/ˌkɒmprɪˈhend/",
            pos = "verb",
            cefr = "B2",
            rank = 850,
            primaryEn = "to grasp mentally; understand thoroughly",
            primaryRu = "постигать, понимать, осознавать",
            primaryUz = "anglamoq, tushunmoq, idrok etmoq",
            formsList = listOf("Past" to "comprehended", "Noun" to "comprehension", "Adjective" to "comprehensible"),
            collocsList = listOf(
                Triple("fail to comprehend", "не быть в состоянии понять", "tushunishga ojiz bo‘lmoq"),
                Triple("fully comprehend", "полностью осознать", "to‘liq anglab yetmoq")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "To understand fully the nature or meaning of something.",
                    defRu = "Полностью понять сущность или значение чего-либо.",
                    defUz = "Biror narsaning mohiyatini chuqur tushunib yetmoq.",
                    register = "general",
                    grammarNotes = "Transitive [T], often with negative verbs",
                    translations = listOf(
                        Triple("ru", "постигать", "глубокий смысл"),
                        Triple("ru", "понимать", "общий смысл"),
                        Triple("uz", "anglamoq", "chuqur mohiyatni"),
                        Triple("uz", "idrok qilmoq", "aql bilan tushunmoq")
                    ),
                    examples = listOf(
                        Triple(
                            "I cannot comprehend why they decided to cancel the project.",
                            "Я не могу понять, почему они решили отменить проект.",
                            "Nima sababdan ular loyihani bekor qilishganini aslo tushuna olmayapman."
                        )
                    ),
                    synonyms = listOf("understand", "fathom", "grasp", "perceive"),
                    antonyms = listOf("misunderstand", "misinterpret")
                )
            )
        )

        // 5. hospitality (noun, B1)
        addEntry(
            headword = "hospitality",
            language = "en",
            ipa = "/ˌhɒspɪˈtæləti/",
            pos = "noun",
            cefr = "B1",
            rank = 620,
            primaryEn = "friendly and generous reception and entertainment of guests",
            primaryRu = "гостеприимство, радушие",
            primaryUz = "mehmonnavozlik, mehmondo‘stlik",
            formsList = listOf("Adjective" to "hospitable", "Adverb" to "hospitably"),
            collocsList = listOf(
                Triple("warm hospitality", "теплое гостеприимство", "samimiy mehmondo‘stlik"),
                Triple("hospitality industry", "гостиничный бизнес", "mehmondo‘stlik sohasi")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "The friendly and welcoming behavior shown towards guests or visitors.",
                    defRu = "Дружелюбный и радушный прием гостей или посетителей.",
                    defUz = "Mehmonlarga ko‘rsatiladigan samimiy va saxiy munosabat.",
                    register = "general",
                    grammarNotes = "Uncountable noun [U]",
                    translations = listOf(
                        Triple("ru", "гостеприимство", "традиция приема гостей"),
                        Triple("ru", "радушие", "сердечность"),
                        Triple("uz", "mehmondo‘stlik", "o‘zbek xalqining eng go‘zal fazilati"),
                        Triple("uz", "mehmonnavozlik", "rasmiy va adabiy")
                    ),
                    examples = listOf(
                        Triple(
                            "We were overwhelmed by the warm hospitality of the local people in Bukhara and Samarkand.",
                            "Мы были поражены теплым гостеприимством местных жителей в Бухаре и Самарканде.",
                            "Bizni Buxoro va Samarqanddagi mahalliy aholining samimiy mehmondo‘stligi lol qoldirdi."
                        )
                    ),
                    synonyms = listOf("warmth", "welcomeness", "friendliness", "generosity"),
                    antonyms = listOf("hostility", "coldness", "unfriendliness")
                )
            )
        )

        // 6. развивать (verb in Russian, B1)
        addEntry(
            headword = "развивать",
            language = "ru",
            ipa = "/rəzvʲɪˈvatʲ/",
            pos = "verb",
            cefr = "B1",
            rank = 920,
            primaryEn = "to develop, expand, improve",
            primaryRu = "способствовать росту, совершенствовать, расширять",
            primaryUz = "rivojlantirmoq, taraqqiy ettirmoq, o‘stirmoq",
            formsList = listOf(
                "Совершенный вид" to "развить",
                "Настоящее время" to "развиваю, развиваешь",
                "Прошедшее" to "развивал, развивала",
                "Причастие" to "развивающий, развиваемый"
            ),
            collocsList = listOf(
                Triple("развивать навыки", "develop skills", "ko‘nikmalarni rivojlantirmoq"),
                Triple("развивать экономику", "develop the economy", "iqtisodiyotni rivojlantirmoq")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "To cause something to grow, mature, or become more advanced.",
                    defRu = "Доводить до более высокой ступени зрелости, совершенства.",
                    defUz = "Biror narsani yanada yuqori, mukammal darajaga yetkazmoq.",
                    register = "general",
                    grammarNotes = "Глагол несовершенного вида, переходный",
                    translations = listOf(
                        Triple("en", "develop", "skills, intellect, industry"),
                        Triple("en", "cultivate", "habits, relationships"),
                        Triple("uz", "rivojlantirmoq", "umumiy ma’no"),
                        Triple("uz", "taraqqiy ettirmoq", "ilm-fan va jamiyat")
                    ),
                    examples = listOf(
                        Triple(
                            "Reading books helps develop critical thinking.",
                            "Чтение книг помогает развивать критическое мышление.",
                            "Kitob o‘qish tanqidiy fikrlashni rivojlantirishga yordam beradi."
                        )
                    ),
                    synonyms = listOf("совершенствовать", "улучшать", "расширять", "культивировать"),
                    antonyms = listOf("угнетать", "разрушать", "тормозить")
                )
            )
        )

        // 7. мудрость (noun in Russian, B2)
        addEntry(
            headword = "мудрость",
            language = "ru",
            ipa = "/ˈmudrəsʲtʲ/",
            pos = "noun",
            cefr = "B2",
            rank = 650,
            primaryEn = "wisdom, sagacity, deep understanding",
            primaryRu = "глубокий ум, опирающийся на жизненный опыт",
            primaryUz = "donolik, donishmandlik, hikmat",
            formsList = listOf(
                "Род" to "Женский род, 3-е склонение",
                "Родительный" to "мудрости",
                "Творительный" to "мудростью"
            ),
            collocsList = listOf(
                Triple("народная мудрость", "folk wisdom", "xalq donoligi"),
                Triple("слова мудрости", "words of wisdom", "hikmatli so‘zlar")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "The quality of having experience, knowledge, and good judgment.",
                    defRu = "Глубокий ум, способность находить верные решения на основе опыта.",
                    defUz = "Hayotiy tajriba va teran aqlga tayangan yuksak fahm-farosat.",
                    register = "general",
                    grammarNotes = "Существительное женского рода",
                    translations = listOf(
                        Triple("en", "wisdom", "general concept"),
                        Triple("uz", "donolik", "aql-idrok"),
                        Triple("uz", "hikmat", "teran ma’no")
                    ),
                    examples = listOf(
                        Triple(
                            "True wisdom comes from experience and reflection.",
                            "Истинная мудрость приходит с опытом и размышлениями.",
                            "Haqiqiy donolik tajriba va mulohaza yuritish orqali keladi."
                        )
                    ),
                    synonyms = listOf("благоразумие", "рассудительность", "дальновидность"),
                    antonyms = listOf("глупость", "невежество", "легкомыслие")
                )
            )
        )

        // 8. tilshunoslik (noun in Uzbek, B2)
        addEntry(
            headword = "tilshunoslik",
            language = "uz",
            ipa = "/tɪlʃʊnɒsˈlɪk/",
            pos = "noun",
            cefr = "B2",
            rank = 780,
            primaryEn = "linguistics, the scientific study of language",
            primaryRu = "лингвистика, языкознание",
            primaryUz = "tilning tuzilishi va qonuniyatlarini o‘rganuvchi fan",
            formsList = listOf(
                "Ko‘plik" to "tilshunosliklar",
                "Qaratqich kelishigi" to "tilshunoslikning",
                "Kirillcha" to "тилшунослик"
            ),
            collocsList = listOf(
                Triple("amaliy tilshunoslik", "прикладная лингвистика", "applied linguistics"),
                Triple("qiyosiy tilshunoslik", "сравнительное языкознание", "comparative linguistics")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "The scientific study of language and its structure.",
                    defRu = "Наука о языке, его структуре, функционировании и развитии.",
                    defUz = "Inson tili, uning tabiati, tuzilishi va tarixiy taraqqiyotini o‘rganuvchi ilmiy soha.",
                    register = "academic",
                    grammarNotes = "O‘zlashma so‘z (til + shunos + lik)",
                    translations = listOf(
                        Triple("en", "linguistics", "scientific field"),
                        Triple("ru", "языкознание", "наука о языке"),
                        Triple("ru", "лингвистика", "научная дисциплина")
                    ),
                    examples = listOf(
                        Triple(
                            "Modern linguistics uses computational methods to analyze big text corpora.",
                            "Современная лингвистика использует компьютерные методы для анализа больших корпусов текстов.",
                            "Zamonaviy tilshunoslik katta matn korpuslarini tahlil qilish uchun hisoblash usullaridan foydalanadi."
                        )
                    ),
                    synonyms = listOf("lingvistika", "til bilimi"),
                    antonyms = emptyList()
                )
            )
        )

        // 9. ilhom (noun in Uzbek, B1)
        addEntry(
            headword = "ilhom",
            language = "uz",
            ipa = "/ɪlˈhɒm/",
            pos = "noun",
            cefr = "B1",
            rank = 690,
            primaryEn = "inspiration, creative impulse",
            primaryRu = "вдохновение, творческий порыв",
            primaryUz = "ijodiy ko‘tarinkilik holati, ilhombaxsh tuyg‘u",
            formsList = listOf(
                "Fe’l shakli" to "ilhomlanmoq",
                "Sifat" to "ilhombaxsh",
                "Kirillcha" to "илҳом"
            ),
            collocsList = listOf(
                Triple("ilhom manbai", "источник вдохновения", "source of inspiration"),
                Triple("ilhom kelmoq", "прийти вдохновению", "to get inspired")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "A sudden brilliant or creative thought, or the state of being stimulated to do something creative.",
                    defRu = "Состояние творческого подъема, прилив творческих сил.",
                    defUz = "Ijodiy faoliyatdagi ruhiy ko‘tarinkilik va sermahsul g‘oyalar tug‘ilishi holati.",
                    register = "general",
                    grammarNotes = "Arabcha o‘zlashma ot",
                    translations = listOf(
                        Triple("en", "inspiration", "creative state"),
                        Triple("ru", "вдохновение", "творческий подъем")
                    ),
                    examples = listOf(
                        Triple(
                            "Nature has always been a great source of inspiration for poets.",
                            "Природа всегда была великим источником вдохновения для поэтов.",
                            "Tabiat har doim shoirlar uchun buyuk ilhom manbai bo‘lib kelgan."
                        )
                    ),
                    synonyms = listOf("jo‘shqinlik", "zavq", "ruhiy ko‘tarinkilik"),
                    antonyms = listOf("tushkunlik", "hafsalasizlik")
                )
            )
        )

        // 10. serendipity (noun in English, C2)
        addEntry(
            headword = "serendipity",
            language = "en",
            ipa = "/ˌserənˈdɪpəti/",
            pos = "noun",
            cefr = "C2",
            rank = 520,
            primaryEn = "the occurrence of events by chance in a happy or beneficial way",
            primaryRu = "счастливая случайность, интуитивная прозорливость",
            primaryUz = "baxtli tasodif, kutilmagan yoqimli topilma",
            formsList = listOf("Adjective" to "serendipitous", "Adverb" to "serendipitously"),
            collocsList = listOf(
                Triple("a stroke of serendipity", "счастливое стечение обстоятельств", "baxtli tasodif natijasi")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "The fact of finding valuable or pleasant things by chance in an unexpected way.",
                    defRu = "Способность делать счастливые случайные открытия или находить удачу непреднамеренно.",
                    defUz = "Rejalashtirilmagan holda, tasodifan yoqimli yoki qimmatli narsani topish baxti.",
                    register = "literary",
                    grammarNotes = "Uncountable noun [U]",
                    translations = listOf(
                        Triple("ru", "счастливая случайность", "удачное стечение"),
                        Triple("uz", "baxtli tasodif", "yoqimli voqea")
                    ),
                    examples = listOf(
                        Triple(
                            "Penicillin was discovered through sheer serendipity.",
                            "Пенициллин был открыт благодаря чистой счастливой случайности.",
                            "Penitsillin mutlaqo baxtli tasodif tufayli kashf etilgan."
                        )
                    ),
                    synonyms = listOf("fluke", "fortune", "providence", "happy accident"),
                    antonyms = listOf("misfortune", "bad luck")
                )
            )
        )

        // 11. diligent (adjective in English, B2)
        addEntry(
            headword = "diligent",
            language = "en",
            ipa = "/ˈdɪlɪdʒənt/",
            pos = "adjective",
            cefr = "B2",
            rank = 760,
            primaryEn = "having or showing care and conscientiousness in one's work",
            primaryRu = "прилежный, старательный, усердный",
            primaryUz = "tirishqoq, tirishqoqlik bilan ishlaydigan, g‘ayratli",
            formsList = listOf("Adverb" to "diligently", "Noun" to "diligence"),
            collocsList = listOf(
                Triple("diligent student", "прилежный студент", "tirishqoq talaba"),
                Triple("diligent research", "тщательное исследование", "qunt bilan olib borilgan tadqiqot")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "Showing steady, earnest, and energetic effort.",
                    defRu = "Проявляющий постоянное старание, усердие и добросовестность.",
                    defUz = "O‘z ishiga yoki o‘qishiga puxta, astoydil va g‘ayrat bilan yondashadigan.",
                    register = "general",
                    grammarNotes = "Descriptive adjective",
                    translations = listOf(
                        Triple("ru", "усердный", "в учебе и труде"),
                        Triple("ru", "старательный", "добросовестный"),
                        Triple("uz", "tirishqoq", "o‘quvchi yoki xodim"),
                        Triple("uz", "quntli", "qunt bilan o‘rganuvchi")
                    ),
                    examples = listOf(
                        Triple(
                            "Through diligent practice, he mastered the complex pronunciation rules.",
                            "Благодаря прилежной практике он освоил сложные правила произношения.",
                            "Tirishqoqlik bilan qilingan mashqlar orqali u murakkab talaffuz qoidalarini to‘liq o‘zlashtirdi."
                        )
                    ),
                    synonyms = listOf("industrious", "assiduous", "hardworking", "meticulous"),
                    antonyms = listOf("lazy", "negligent", "careless")
                )
            )
        )

        // 12. samimiyat (noun in Uzbek, B1)
        addEntry(
            headword = "samimiyat",
            language = "uz",
            ipa = "/sæmɪmɪˈjæt/",
            pos = "noun",
            cefr = "B1",
            rank = 830,
            primaryEn = "sincerity, genuineness, warmth",
            primaryRu = "искренность, душевность, чистосердечие",
            primaryUz = "chin dildan bo‘lgan munosabat, soxtalikdan xolilik",
            formsList = listOf(
                "Sifat" to "samimiy",
                "Ravish" to "samimiyat bilan",
                "Kirillcha" to "самимият"
            ),
            collocsList = listOf(
                Triple("samimiyat bilan gapirmoq", "говорить с искренностью", "speak with sincerity")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "The quality of being free from pretense, deceit, or hypocrisy.",
                    defRu = "Качество человека, выражающееся в правдивости, открытости и отсутствии притворства.",
                    defUz = "Kishining ichki dunyosi va xatti-harakatidagi ochiqlik, to‘g‘rilik va samimiylik.",
                    register = "general",
                    grammarNotes = "Mavhum ot",
                    translations = listOf(
                        Triple("en", "sincerity", "warmth of heart"),
                        Triple("ru", "искренность", "открытость души")
                    ),
                    examples = listOf(
                        Triple(
                            "Her sincerity immediately put everyone at ease.",
                            "Ее искренность сразу же расположила всех к себе.",
                            "Uning samimiyati darhol barchaning ko‘nglini erkin va quvnoq qildi."
                        )
                    ),
                    synonyms = listOf("dillik", "ochiqko‘ngillilik", "beg‘uborlik"),
                    antonyms = listOf("soxtalik", "munofiqlik", "ikkiyuzlamachilik")
                )
            )
        )

        // 13. созидание (noun in Russian, C1)
        addEntry(
            headword = "созидание",
            language = "ru",
            ipa = "/səzʲɪˈdanʲɪje/",
            pos = "noun",
            cefr = "C1",
            rank = 540,
            primaryEn = "creation, constructive work, building",
            primaryRu = "творческий созидательный процесс, творение нового",
            primaryUz = "bunyodkorlik, yaratuvchanlik, ijod",
            formsList = listOf(
                "Глагол" to "созидать",
                "Прилагательное" to "созидательный"
            ),
            collocsList = listOf(
                Triple("дух созидания", "spirit of creation", "bunyodkorlik ruhi"),
                Triple("созидательный труд", "constructive labor", "yaratuvchan mehnat")
            ),
            senses = listOf(
                SenseSpec(
                    defEn = "The process of creating or building something beneficial and lasting.",
                    defRu = "Деятельность по созданию чего-либо ценного, возвышенного.",
                    defUz = "Jamiyat va insoniyat uchun foydali, ezgu narsalarni yaratish faoliyati.",
                    register = "elevated",
                    grammarNotes = "Существительное среднего рода",
                    translations = listOf(
                        Triple("en", "creation", "constructive work"),
                        Triple("uz", "bunyodkorlik", "qurish va yaratish")
                    ),
                    examples = listOf(
                        Triple(
                            "Science and education are the true foundations of societal creation.",
                            "Наука и образование — истинные основы созидания общества.",
                            "Ilm-fan va ta’lim jamiyat bunyodkorligining haqiqiy poydevoridir."
                        )
                    ),
                    synonyms = listOf("творение", "строительство", "сотворение"),
                    antonyms = listOf("разрушение", "деструкция", "уничтожение")
                )
            )
        )

        return PreloadBundle(
            entries = entries,
            meanings = meanings,
            translations = translations,
            examples = examples,
            synonyms = synonyms,
            forms = forms,
            collocations = collocations
        )
    }

    data class SenseSpec(
        val defEn: String,
        val defRu: String,
        val defUz: String,
        val register: String = "general",
        val grammarNotes: String = "",
        val translations: List<Triple<String, String, String>> = emptyList(), // targetLang, text, note
        val examples: List<Triple<String, String, String>> = emptyList(), // en, ru, uz
        val synonyms: List<String> = emptyList(),
        val antonyms: List<String> = emptyList()
    )
}
