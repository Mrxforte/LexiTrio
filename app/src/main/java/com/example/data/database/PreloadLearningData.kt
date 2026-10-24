package com.example.data.database

import com.example.data.model.ConversationEntity
import com.example.data.model.ConversationTurnEntity
import com.example.data.model.GrammarLessonEntity
import com.example.data.model.QuizQuestionEntity
import com.example.data.model.QuizType
import com.example.data.model.SavedWordEntity
import com.example.data.model.TopicEntity
import com.example.data.model.TopicWordCrossRef
import com.example.data.model.VocabularyCollectionEntity

object PreloadLearningData {

    fun getInitialCollections(): List<VocabularyCollectionEntity> {
        return listOf(
            VocabularyCollectionEntity(id = 1, title = "Barcha saqlangan so‘zlar", description = "All saved vocabulary items", iconName = "all_inbox", isCustom = false),
            VocabularyCollectionEntity(id = 2, title = "Oxford 3000 Essentials", description = "Core high-frequency lexical foundation", iconName = "school", isCustom = false),
            VocabularyCollectionEntity(id = 3, title = "Sayohat va Aeroport (Travel)", description = "Essential words for flights, hotels, and navigation", iconName = "flight", isCustom = false),
            VocabularyCollectionEntity(id = 4, title = "IELTS & Academic Lexicon", description = "Advanced vocabulary for examinations", iconName = "psychology", isCustom = false),
            VocabularyCollectionEntity(id = 5, title = "O‘zbekcha ibora va idiomalari", description = "Colloquial Uzbek idioms and proverbs", iconName = "auto_awesome", isCustom = false)
        )
    }

    fun getInitialSavedWords(): List<SavedWordEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            SavedWordEntity(
                id = 1,
                entryId = 1,
                headword = "acquire",
                language = "en",
                partOfSpeech = "verb",
                phoneticIpa = "/əˈkwaɪər/",
                definition = "To buy, gain or obtain through effort or skill",
                translation = "qo‘lga kiritmoq, o‘zlashtirmoq",
                exampleSentence = "She acquired fluency through immersion.",
                collectionId = 2,
                savedAt = now - 86400000L,
                isFavorite = true,
                nextReviewTimestamp = now - 1000L // Due right away for review!
            ),
            SavedWordEntity(
                id = 2,
                entryId = 2,
                headword = "resilience",
                language = "en",
                partOfSpeech = "noun",
                phoneticIpa = "/rɪˈzɪliəns/",
                definition = "The capacity to recover quickly from difficulties",
                translation = "bardoshlilik, matonat",
                exampleSentence = "Courage and resilience help overcome adversity.",
                collectionId = 4,
                savedAt = now - 43200000L,
                isFavorite = true,
                nextReviewTimestamp = now - 500L
            ),
            SavedWordEntity(
                id = 3,
                entryId = 5,
                headword = "hospitality",
                language = "en",
                partOfSpeech = "noun",
                phoneticIpa = "/ˌhɒspɪˈtæləti/",
                definition = "Friendly and generous reception of guests",
                translation = "mehmondo‘stlik, samimiylik",
                exampleSentence = "Uzbek hospitality is renowned worldwide.",
                collectionId = 3,
                savedAt = now - 20000000L,
                isFavorite = true,
                nextReviewTimestamp = now + 86400000L
            )
        )
    }

    fun getInitialTopics(): List<TopicEntity> {
        return listOf(
            TopicEntity(
                id = 1,
                titleEn = "Daily Life & Routines",
                titleRu = "Повседневная жизнь и распорядок",
                titleUzLatn = "Kundalik hayot va odatlar",
                titleUzCyrl = "Кундалик ҳаёт ва одатлар",
                category = "General",
                iconName = "today",
                descriptionEn = "Essential words for talking about your everyday schedule, morning habits, and domestic chores.",
                descriptionRu = "Основные слова для описания ежедневного расписания, привычек и домашнего быта.",
                descriptionUzLatn = "Har kungi ish tartibi, ertalabki odatlar va maishiy yumushlar uchun zaruriy so‘zlar to‘plami.",
                cefrLevel = "A1",
                estimatedMinutes = 8,
                totalWords = 15
            ),
            TopicEntity(
                id = 2,
                titleEn = "Airports & International Flights",
                titleRu = "Аэропорты и международные перелеты",
                titleUzLatn = "Aeroport va xalqaro parvozlar",
                titleUzCyrl = "Аэропорт ва халқаро парвозлар",
                category = "Travel",
                iconName = "flight_takeoff",
                descriptionEn = "Boarding passes, luggage check-in, customs control, layovers, and in-flight etiquette.",
                descriptionRu = "Посадочные талоны, сдача багажа, таможенный контроль, пересадки и правила на борту.",
                descriptionUzLatn = "Samolyotga chiqish taloni, bagaj topshirish, bojxona nazorati va parvozdagi qulayliklar.",
                cefrLevel = "A2",
                estimatedMinutes = 12,
                totalWords = 18
            ),
            TopicEntity(
                id = 3,
                titleEn = "Food, Dining & Restaurants",
                titleRu = "Еда, кулинария и рестораны",
                titleUzLatn = "Taomlar, pazandachilik va restoranlar",
                titleUzCyrl = "Таомлар, пазандачилик ва ресторанлар",
                category = "Food",
                iconName = "restaurant",
                descriptionEn = "Ordering meals, asking for dietary recommendations, paying the bill, and traditional cuisine.",
                descriptionRu = "Заказ блюд, диетические предпочтения, оплата счета и национальная кухня.",
                descriptionUzLatn = "Buyurtma berish, hisob-kitob qilish, milliy taomlar va mehmondorchilik odoblari.",
                cefrLevel = "A2",
                estimatedMinutes = 10,
                totalWords = 20
            ),
            TopicEntity(
                id = 4,
                titleEn = "Technology, AI & Programming",
                titleRu = "Технологии, ИИ и программирование",
                titleUzLatn = "Texnologiya, SI va dasturlash",
                titleUzCyrl = "Технология, СИ ва дастурлаш",
                category = "Tech",
                iconName = "code",
                descriptionEn = "Software development, neural networks, cloud computing, algorithms, and cybersecurity.",
                descriptionRu = "Разработка ПО, нейросети, облачные вычисления, алгоритмы и кибербезопасность.",
                descriptionUzLatn = "Dasturiy ta’minot, neyron tarmoqlar, bulutli texnologiyalar va axborot xavfsizligi.",
                cefrLevel = "B2",
                estimatedMinutes = 15,
                totalWords = 25
            ),
            TopicEntity(
                id = 5,
                titleEn = "Healthcare, Medicine & Emergencies",
                titleRu = "Здравоохранение, медицина и скорая помощь",
                titleUzLatn = "Sog‘liqni saqlash, tibbiyot va tez yordam",
                titleUzCyrl = "Соғлиқни сақлаш, тиббиёт ва тез ёрдам",
                category = "Health",
                iconName = "medical_services",
                descriptionEn = "Symptoms, doctor appointments, pharmacy prescriptions, and emergency calls.",
                descriptionRu = "Симптомы, прием у врача, рецепты в аптеке и вызов экстренных служб.",
                descriptionUzLatn = "Kasal alomatlari, shifokor qabulida bo‘lish, dorixona va shoshilinch yordam chaqirish.",
                cefrLevel = "B1",
                estimatedMinutes = 10,
                totalWords = 16
            ),
            TopicEntity(
                id = 6,
                titleEn = "Business Meetings & Negotiations",
                titleRu = "Деловые встречи и переговоры",
                titleUzLatn = "Ishbilarmonlik uchrashuvlari va muzokaralar",
                titleUzCyrl = "Ишбилармонлик учрашувлари ва музокаралар",
                category = "Business",
                iconName = "business_center",
                descriptionEn = "Agendas, contract terms, pitching ideas, diplomacy, and strategic partnership.",
                descriptionRu = "Повестка дня, условия контракта, презентация идей и деловой этикет.",
                descriptionUzLatn = "Kun tartibi, shartnoma shartlari, g‘oyalarni taqdim etish va o‘zaro hamkorlik.",
                cefrLevel = "B2",
                estimatedMinutes = 14,
                totalWords = 22
            ),
            TopicEntity(
                id = 7,
                titleEn = "Everyday Idioms & Phrasal Verbs",
                titleRu = "Повседневные идиомы и фразовые глаголы",
                titleUzLatn = "Kundalik idiomalar va frazeologik fe’llar",
                titleUzCyrl = "Кундалик идиомалар ва фразеологик феъллар",
                category = "Advanced",
                iconName = "record_voice_over",
                descriptionEn = "Figurative expressions, natural spoken English, Russian phraseology, and Uzbek matallar.",
                descriptionRu = "Фигуральные выражения, живая речь, русская фразеология и узбекские пословицы.",
                descriptionUzLatn = "Ko‘chma ma’nodagi iboralar, jonli so‘zlashuv nutqi va xalq maqollari.",
                cefrLevel = "C1",
                estimatedMinutes = 15,
                totalWords = 30
            ),
            TopicEntity(
                id = 8,
                titleEn = "Nature, Weather & Environment",
                titleRu = "Природа, погода и экология",
                titleUzLatn = "Tabiat, ob-havo va atrof-muhit",
                titleUzCyrl = "Табиат, об-ҳаво ва атроф-муҳит",
                category = "General",
                iconName = "wb_sunny",
                descriptionEn = "Climate change, weather forecasts, flora and fauna, and seasonal wonders.",
                descriptionRu = "Изменение климата, прогноз погоды, флора, фауна и времена года.",
                descriptionUzLatn = "Iqlim o‘zgarishi, ob-havo ma’lumotlari, o‘simlik va hayvonot olami, fasllar go‘zalligi.",
                cefrLevel = "A2",
                estimatedMinutes = 8,
                totalWords = 14
            )
        )
    }

    fun getInitialTopicCrossRefs(): List<TopicWordCrossRef> {
        return listOf(
            TopicWordCrossRef(1, 1),
            TopicWordCrossRef(4, 1),
            TopicWordCrossRef(2, 5),
            TopicWordCrossRef(3, 5),
            TopicWordCrossRef(6, 2),
            TopicWordCrossRef(4, 4),
            TopicWordCrossRef(7, 3),
            TopicWordCrossRef(6, 3)
        )
    }

    fun getInitialConversations(): List<Pair<ConversationEntity, List<ConversationTurnEntity>>> {
        return listOf(
            Pair(
                ConversationEntity(
                    id = 1,
                    titleEn = "Checking in at an International Hotel",
                    titleRu = "Заселение в международный отель",
                    titleUzLatn = "Xalqaro mehmonxonaga joylashish",
                    titleUzCyrl = "Халқаро меҳмонхонага жойлашиш",
                    situationEn = "A guest arrives at the hotel reception in Tashkent to check in for a three-night stay.",
                    situationRu = "Гость прибывает на ресепшн отеля в Ташкенте, чтобы оформить трехдневное проживание.",
                    situationUzLatn = "Mehmon Toshkentdagi mehmonxona qabulxonasiga kelib, 3 kunga xona rasmiylashtirmoqda.",
                    category = "Travel",
                    difficulty = "A2",
                    iconName = "hotel",
                    speakerOneName = "Receptionist (Qabulxona xodimi)",
                    speakerTwoName = "Guest (Mehmon)"
                ),
                listOf(
                    ConversationTurnEntity(
                        id = 1,
                        conversationId = 1,
                        turnIndex = 1,
                        speakerRole = "Receptionist",
                        isSpeakerOne = true,
                        textEn = "Good evening! Welcome to Hotel Uzbekistan. How may I assist you today?",
                        textRu = "Добрый вечер! Добро пожаловать в отель «Узбекистан». Чем я могу вам помочь?",
                        textUz = "Xayrli kech! «O‘zbekiston» mehmonxonasiga xush kelibsiz. Sizga qanday yordam bera olaman?",
                        culturalNote = "Polite hospitality greeting in Central Asia"
                    ),
                    ConversationTurnEntity(
                        id = 2,
                        conversationId = 1,
                        turnIndex = 2,
                        speakerRole = "Guest",
                        isSpeakerOne = false,
                        textEn = "Good evening. I have a reservation under the name John Carter for three nights.",
                        textRu = "Добрый вечер. У меня забронирован номер на имя Джон Картер на три ночи.",
                        textUz = "Xayrli kech. Men uch kechaga Jon Karter nomiga xona band qilgan edim.",
                        culturalNote = "Presenting reservation details"
                    ),
                    ConversationTurnEntity(
                        id = 3,
                        conversationId = 1,
                        turnIndex = 3,
                        speakerRole = "Receptionist",
                        isSpeakerOne = true,
                        textEn = "Thank you, Mr. Carter. May I please see your passport and immigration slip?",
                        textRu = "Спасибо, господин Картер. Могу я взглянуть на ваш паспорт и иммиграционный бланк?",
                        textUz = "Rahmat, janob Karter. Iltimos, pasportingizni ko‘rsatsangiz?",
                        culturalNote = "Hotel registration requirement"
                    ),
                    ConversationTurnEntity(
                        id = 4,
                        conversationId = 1,
                        turnIndex = 4,
                        speakerRole = "Guest",
                        isSpeakerOne = false,
                        textEn = "Certainly, here you go. Could you tell me if breakfast is included, and what time it starts?",
                        textRu = "Конечно, вот, пожалуйста. Скажите, включен ли завтрак и во сколько он начинается?",
                        textUz = "Albatta, marhamat. Ayting-chi, nonushta xona narxiga kiritilganmi va u soat nechida boshlanadi?",
                        culturalNote = "Breakfast dining question"
                    ),
                    ConversationTurnEntity(
                        id = 5,
                        conversationId = 1,
                        turnIndex = 5,
                        speakerRole = "Receptionist",
                        isSpeakerOne = true,
                        textEn = "Yes, complimentary buffet breakfast is served on the second floor from 7:00 AM to 10:30 AM. Here is your keycard for room 412.",
                        textRu = "Да, бесплатный завтрак «шведский стол» подается на втором этаже с 7:00 до 10:30. Вот ваш электронный ключ от номера 412.",
                        textUz = "Ha, bepul shved stoli nonushtasi 2-qavatda soat 7:00 dan 10:30 gacha taqdim etiladi. Mana, 412-xona kalit kartasi.",
                        culturalNote = "Handing keycard and directions"
                    )
                )
            ),
            Pair(
                ConversationEntity(
                    id = 2,
                    titleEn = "Ordering at a Traditional Teahouse (Choyxona)",
                    titleRu = "Заказ в традиционной чайхане",
                    titleUzLatn = "Choyxonada milliy taomlar buyurtma qilish",
                    titleUzCyrl = "Чойхонада миллий таомлар буюртма қилиш",
                    situationEn = "Friends visit a renowned Tashkent choyxona to savor authentic plov, somsa, and green tea.",
                    situationRu = "Друзья пришли в чайхану, чтобы отведать настоящий плов, сомсу и зеленый чай.",
                    situationUzLatn = "Do‘stlar mazali palov, somsa va ko‘k choy buyurtma qilish uchun choyxonaga kirishdi.",
                    category = "Dining",
                    difficulty = "A2",
                    iconName = "restaurant",
                    speakerOneName = "Waiter (Ofitsiant)",
                    speakerTwoName = "Customer (Mijoz)"
                ),
                listOf(
                    ConversationTurnEntity(
                        id = 6,
                        conversationId = 2,
                        turnIndex = 1,
                        speakerRole = "Waiter",
                        isSpeakerOne = true,
                        textEn = "Assalomu alaykum! Welcome, dear guests. Please take a seat on the tapchan.",
                        textRu = "Ассалому алейкум! Добро пожаловать, дорогие гости. Присаживайтесь на топчан.",
                        textUz = "Assalomu alaykum! Xush kelibsiz, aziz mehmonlar. Marhamat, so‘riga o‘tiringizlar.",
                        culturalNote = "Traditional Central Asian tapchan seating"
                    ),
                    ConversationTurnEntity(
                        id = 7,
                        conversationId = 2,
                        turnIndex = 2,
                        speakerRole = "Customer",
                        isSpeakerOne = false,
                        textEn = "Thank you! We would like to try your wedding plov and crispy tandoor somsa.",
                        textRu = "Спасибо! Мы бы хотели попробовать ваш праздничный плов и хрустящую тандырную сомсу.",
                        textUz = "Rahmat! Biz to‘y oshi (palov) va tandirda pishgan tansiq somsalaringizdan tatib ko‘rmoqchimiz.",
                        culturalNote = "National culinary pride"
                    ),
                    ConversationTurnEntity(
                        id = 8,
                        conversationId = 2,
                        turnIndex = 3,
                        speakerRole = "Waiter",
                        isSpeakerOne = true,
                        textEn = "Excellent choice. Would you prefer hot green tea with lemon, or black tea?",
                        textRu = "Прекрасный выбор. Желаете горячий зеленый чай с лимоном или черный чай?",
                        textUz = "Ajoyib tanlov. Ko‘k choyga limon qo‘shib damlab kelaymi yoki qora choy ma’qulmi?",
                        culturalNote = "Green tea is an essential digestive companion"
                    ),
                    ConversationTurnEntity(
                        id = 9,
                        conversationId = 2,
                        turnIndex = 4,
                        speakerRole = "Customer",
                        isSpeakerOne = false,
                        textEn = "Green tea in a traditional teapot, please. And could you bring some fresh flatbread (non)?",
                        textRu = "Зеленый чай в традиционном чайнике, пожалуйста. И принесите горячую лепешку.",
                        textUz = "Choynakda damlangan ko‘k choy bo‘lsin, iltimos. Yoniga yangi uzilgan issiq patir non ham keltirsangiz.",
                        culturalNote = "Respect for bread (non) is sacred in Uzbek culture"
                    )
                )
            ),
            Pair(
                ConversationEntity(
                    id = 3,
                    titleEn = "Software Engineer Job Interview",
                    titleRu = "Собеседование на должность разработчика",
                    titleUzLatn = "Dasturchilik bo‘yicha ish suhbati",
                    titleUzCyrl = "Дастурчилик бўйича иш суҳбати",
                    situationEn = "A candidate discusses Android architecture, coroutines, and problem-solving with a tech lead.",
                    situationRu = "Кандидат обсуждает архитектуру Android, корутины и решение задач с техлидом.",
                    situationUzLatn = "Nomzod bosh muhandis bilan Android arxitekturasi va koroutinlar haqida suhbatlashmoqda.",
                    category = "Career",
                    difficulty = "B2",
                    iconName = "work",
                    speakerOneName = "Tech Lead (Texnik rahbar)",
                    speakerTwoName = "Candidate (Nomzod)"
                ),
                listOf(
                    ConversationTurnEntity(
                        id = 10,
                        conversationId = 3,
                        turnIndex = 1,
                        speakerRole = "Tech Lead",
                        isSpeakerOne = true,
                        textEn = "Could you walk us through how you manage state and asynchronous operations in Jetpack Compose?",
                        textRu = "Расскажите, как вы управляете состоянием и асинхронными операциями в Jetpack Compose?",
                        textUz = "Jetpack Compose muhitida holatni boshqarish va asinxron jarayonlarni qanday tashkil qilganingizni so‘zlab bersangiz?",
                        culturalNote = "Standard senior architectural interview inquiry"
                    ),
                    ConversationTurnEntity(
                        id = 11,
                        conversationId = 3,
                        turnIndex = 2,
                        speakerRole = "Candidate",
                        isSpeakerOne = false,
                        textEn = "I employ clean MVVM architecture, exposing UI state via StateFlow from the ViewModel and collecting it with lifecycle awareness.",
                        textRu = "Я использую чистую архитектуру MVVM, передавая состояние через StateFlow из ViewModel с учетом жизненного цикла.",
                        textUz = "Men toza MVVM arxitekturasiga tayanaman: ViewModel ichidan StateFlow orqali holatni uzatib, Compose ekranlarida uni xavfsiz qabul qilaman.",
                        culturalNote = "Professional engineering explanation"
                    )
                )
            )
        )
    }

    fun getInitialGrammarLessons(): List<GrammarLessonEntity> {
        return listOf(
            GrammarLessonEntity(
                id = 1,
                titleEn = "English Articles: A, An, and The",
                titleRu = "Артикли в английском: A, An и The",
                titleUzLatn = "Ingliz tili artikllari: A, An va The",
                titleUzCyrl = "Инглиз тили артисллари: A, An ва The",
                category = "Nouns & Articles",
                level = "A1",
                summaryEn = "Master indefinite (a/an) and definite (the) article usage rules with clear comparisons to Russian and Uzbek.",
                summaryRu = "Правила употребления неопределенных (a/an) и определенного (the) артиклей в сравнении с русским и узбекским.",
                summaryUzLatn = "Noaniq (a/an) va aniq (the) artikllarining qo‘llanish qoidalari va o‘zbek tilidagi ekvivalentlari.",
                ruleDetailsJson = """
                    ### 1. The Indefinite Article: A / An
                    Used before singular countable nouns when mentioned for the first time:
                    - **a** before consonant sounds: *a book, a university* (/juː/ is consonant sound!)
                    - **an** before vowel sounds: *an apple, an hour* (/aʊər/ has silent 'h'!)
                    
                    *O‘zbek tilida*: Ko‘pincha «bir» so‘zi yoki shunchaki otning o‘zi orqali ifodalanadi (*Men **bir** kitob o‘qidim*).
                    *На русском*: Соответствует словам «какой-то», «один», либо опускается (*Я прочитал книгу*).
                    
                    ### 2. The Definite Article: The
                    Used when both the speaker and listener know specifically what is being referred to:
                    - *The book on the table* (Ushbu stol ustidagi o‘sha kitob / Та самая книга на столе).
                    - Unique entities: *the sun, the moon, the world*.
                    
                    ### 3. Zero Article
                    Do not use articles with plurals or uncountable nouns in general statements:
                    - *Water is life.* (Suv bu hayot / Вода — это жизнь).
                """.trimIndent()
            ),
            GrammarLessonEntity(
                id = 2,
                titleEn = "Uzbek Agglutinative Suffixes & Vowel Harmony",
                titleRu = "Агглютинативные суффиксы в узбекском языке",
                titleUzLatn = "O‘zbek tili agglyutinativ qo‘shimchalari va kelishiklar",
                titleUzCyrl = "Ўзбек тили агглютинатив қўшимчалари ва келишиклар",
                category = "Uzbek Suffixes",
                level = "B1",
                summaryEn = "Understand how suffixes stack logically in Uzbek (noun + plural + possessive + case) without irregular gender changes.",
                summaryRu = "Понимание логики присоединения суффиксов в узбекском: основа + мн. число + принадлежность + падеж.",
                summaryUzLatn = "O‘zbek tilida qo‘shimchalarning zanjir kabi ulanish tartibi: Ot + ko‘plik + egalik + kelishik.",
                ruleDetailsJson = """
                    ### Suffix Stacking Order in Uzbek
                    Unlike English or Russian, Uzbek adds suffixes sequentially in a strict predictable order:
                    
                    **Base Word + Plural (-lar) + Possessive (-im/-ing/-i) + Case (-da/-dan/-ga/-ni/-ning)**
                    
                    *Example*:
                    - `kitob` (book / книга)
                    - `kitob` + `lar` = `kitoblar` (books / книги)
                    - `kitob` + `lar` + `im` = `kitoblarim` (my books / мои книги)
                    - `kitob` + `lar` + `im` + `da` = `kitoblarimda` (in my books / в моих книгах)
                    
                    ### 6 Cases in Uzbek:
                    1. **Bosh kelishik** (Nominative): *kitob* (nol qo‘shimcha)
                    2. **Qaratqich** (Genitive -ning): *kitobning* (of the book / книги)
                    3. **Tushum** (Accusative -ni): *kitobni* (the book as direct object / книгу)
                    4. **Jo‘nalish** (Dative -ga): *kitobga* (to the book / к книге)
                    5. **O‘rin-payt** (Locative -da): *kitobda* (in/on the book / в книге)
                    6. **Chiqish** (Ablative -dan): *kitobdan* (from the book / из книги)
                """.trimIndent()
            ),
            GrammarLessonEntity(
                id = 3,
                titleEn = "Russian Grammatical Gender & Cases",
                titleRu = "Род и падежи в русском языке",
                titleUzLatn = "Rus tilida grammatik jins (Rod) va kelishiklar",
                titleUzCyrl = "Рус тилида грамматик жинс (Род) ва келишиклар",
                category = "Russian Cases",
                level = "B1",
                summaryEn = "Master masculine, feminine, and neuter nouns and their six case declension endings.",
                summaryRu = "Мужской, женский и средний род, а также 6 падежей русского языка и их окончания.",
                summaryUzLatn = "Rus tilidagi uchta rod (mujskoy, jenskiy, sredniy) va 6 ta kelishikning tuslanishi.",
                ruleDetailsJson = """
                    ### 3 Grammatical Genders in Russian:
                    1. **Мужской род (Masculine)**: ends in consonant or -й: *город, чай, словарь*
                    2. **Женский род (Feminine)**: ends in -а, -я, or soft sign -ь: *книга, земля, радость*
                    3. **Средний род (Neuter)**: ends in -о, -е, -мя: *окно, здание, время*
                    
                    ### Russian Case System (6 Cases):
                    - **Именительный (Кто? Что?)**: *студент читает*
                    - **Родительный (Кого? Чего?)**: *нет студента*
                    - **Дательный (Кому? Чему?)**: *дать студенту*
                    - **Винительный (Кого? Что?)**: *вижу студента*
                    - **Творительный (Кем? Чем?)**: *горжусь студентом*
                    - **Предложный (О ком? О чем?)**: *думаю о студенте*
                """.trimIndent()
            ),
            GrammarLessonEntity(
                id = 4,
                titleEn = "Present Perfect vs. Past Simple in English",
                titleRu = "Present Perfect и Past Simple в английском",
                titleUzLatn = "Present Perfect va Past Simple zamonlari farqi",
                titleUzCyrl = "Present Perfect ва Past Simple замонлари фарқи",
                category = "Verb Tenses",
                level = "B1",
                summaryEn = "Definite past time markers vs. present relevance and life experiences.",
                summaryRu = "Разница между действием с точным указанием времени в прошлом и результатом в настоящем.",
                summaryUzLatn = "O‘tmishda aniq vaqtda tugallangan ish-harakat va natijasi hozirgi zamonga bog‘liq ish-harakat farqi.",
                ruleDetailsJson = """
                    ### Past Simple (Finished Time)
                    Used when a specific past time is stated or implied (*yesterday, in 2021, two days ago, when I was young*):
                    - *I visited Samarkand last year.* (Men o‘tgan yili Samarqandga bordim).
                    
                    ### Present Perfect (Connection to Now / Life Experience)
                    Formed with `have/has + Past Participle (V3)`. Used when the result matters now, or the time is open:
                    - *I have visited Samarkand twice.* (Men Samarqandda ikki marta bo‘lganman — hayotiy tajriba).
                    - *She has lost her key.* (U kalitini yo‘qotib qo‘ydi — natijada hozir eshikni ocha olmayapti).
                """.trimIndent()
            )
        )
    }

    fun getInitialQuizQuestions(): List<QuizQuestionEntity> {
        return listOf(
            QuizQuestionEntity(
                id = 1,
                type = QuizType.WORD_TO_TRANSLATION,
                questionText = "What is the primary Uzbek translation of the English word 'resilience'?",
                promptAudioText = "resilience",
                promptLanguage = "en",
                correctAnswer = "bardoshlilik, matonat",
                optionA = "bardoshlilik, matonat",
                optionB = "xushmuomalalik",
                optionC = "kutilmagan topilma",
                optionD = "dangasalik",
                explanation = "'Resilience' means the capacity to recover quickly from difficulties (matonat, bardoshlilik).",
                category = "Vocabulary",
                difficulty = "B2"
            ),
            QuizQuestionEntity(
                id = 2,
                type = QuizType.WORD_TO_TRANSLATION,
                questionText = "Как переводится на русский язык слово 'hospitality'?",
                promptAudioText = "hospitality",
                promptLanguage = "en",
                correctAnswer = "гостеприимство, радушие",
                optionA = "гостеприимство, радушие",
                optionB = "больничное лечение",
                optionC = "невежество",
                optionD = "враждебность",
                explanation = "'Hospitality' обозначает радушный прием гостей (гостеприимство).",
                category = "Vocabulary",
                difficulty = "A2"
            ),
            QuizQuestionEntity(
                id = 3,
                type = QuizType.CHOOSE_DEFINITION,
                questionText = "Which definition correctly describes 'eloquent'?",
                promptAudioText = "eloquent",
                promptLanguage = "en",
                correctAnswer = "Fluent and persuasive in speaking or writing",
                optionA = "Fluent and persuasive in speaking or writing",
                optionB = "Unable to make quick decisions",
                optionC = "Easily frightened by sudden events",
                optionD = "Relating to deep geological formations",
                explanation = "'Eloquent' means expressing ideas persuasively and beautifully (fasih, chechan).",
                category = "Definition",
                difficulty = "C1"
            ),
            QuizQuestionEntity(
                id = 4,
                type = QuizType.LISTENING_CHALLENGE,
                questionText = "Listen to the word and choose the correct Russian and Uzbek translation:",
                promptAudioText = "comprehend",
                promptLanguage = "en",
                correctAnswer = "понимать, постигать • anglamoq",
                optionA = "понимать, постигать • anglamoq",
                optionB = "приобретать • sotib olmoq",
                optionC = "радоваться • quvonmoq",
                optionD = "сравнивать • solishtirmoq",
                explanation = "'Comprehend' means to understand thoroughly (tushunmoq, idrok qilmoq).",
                category = "Listening",
                difficulty = "B2"
            ),
            QuizQuestionEntity(
                id = 5,
                type = QuizType.FILL_IN_BLANK,
                questionText = "Complete the sentence: 'She has ______ a deep working knowledge of linguistics.'",
                promptAudioText = "She has acquired a deep working knowledge of linguistics.",
                promptLanguage = "en",
                correctAnswer = "acquired",
                optionA = "acquired",
                optionB = "forgotten",
                optionC = "declined",
                optionD = "escaped",
                explanation = "'To acquire knowledge' is the standard collocation for gaining understanding.",
                category = "Collocations",
                difficulty = "B2"
            ),
            QuizQuestionEntity(
                id = 6,
                type = QuizType.GRAMMAR_CHECK,
                questionText = "Qaysi gapda noaniq artikl (a/an) to‘g‘ri qo‘llangan?",
                promptAudioText = "an honest person",
                promptLanguage = "en",
                correctAnswer = "She is an honest and diligent person.",
                optionA = "She is an honest and diligent person.",
                optionB = "She is a honest person.",
                optionC = "He works as an university teacher.",
                optionD = "They bought an house yesterday.",
                explanation = "'Honest' so‘zi unli tovush (/ɒ/) bilan boshlangani sababli 'an' artikli ishlatiladi.",
                category = "Grammar",
                difficulty = "B1"
            ),
            QuizQuestionEntity(
                id = 7,
                type = QuizType.SYNONYM_ANTONYM,
                questionText = "What is an antonym of 'diligent'?",
                promptAudioText = "diligent",
                promptLanguage = "en",
                correctAnswer = "lazy / negligent",
                optionA = "lazy / negligent",
                optionB = "industrious",
                optionC = "meticulous",
                optionD = "creative",
                explanation = "'Diligent' means hardworking; its direct antonym is lazy or negligent.",
                category = "Synonyms & Antonyms",
                difficulty = "B2"
            )
        )
    }
}
