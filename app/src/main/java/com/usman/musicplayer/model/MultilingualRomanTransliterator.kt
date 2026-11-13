package com.usman.musicplayer.model

/**
 * User-selectable display script mode for USMAN MUSIC synchronized lyrics.
 */
enum class LyricsScriptMode(val displayName: String, val shortLabel: String) {
    ORIGINAL(displayName = "Original Script", shortLabel = "Original"),
    ROMANIZED(displayName = "Romanized (English Letters)", shortLabel = "Romanized")
}

/**
 * Universal, language-specific phonetic transliteration engine for USMAN MUSIC.
 *
 * Converts lyrics across all supported languages into Romanized (English alphabet) text
 * using language-specific phonology rules and curated vocabulary lexicons without translating
 * meaning or altering word/syllable synchronization timestamps:
 * - Telugu (U+0C00..U+0C7F)
 * - Hindi / Marathi / Bhojpuri / Sanskrit (Devanagari U+0900..U+097F)
 * - Tamil (U+0B80..U+0BFF)
 * - Malayalam (U+0D00..U+0D7F)
 * - Kannada (U+0C80..U+0CFF)
 * - Bengali / Assamese (U+0980..U+09FF)
 * - Punjabi (Gurmukhi U+0A00..U+0A7F)
 * - Gujarati (U+0A80..U+0AFF)
 * - Odia (U+0B00..U+0B7F)
 * - Urdu / Arabic (U+0600..U+06FF, U+0750..U+077F, U+FB50..U+FEFF)
 * - Korean (Hangul U+AC00..U+D7A3)
 * - Japanese (Hiragana U+3040..U+309F, Katakana U+30A0..U+30FF)
 * - English / Spanish / French (Latin scripts preserved intact)
 */
object MultilingualRomanTransliterator {

    enum class DetectedScript {
        LATIN,
        DEVANAGARI,
        TELUGU,
        TAMIL,
        MALAYALAM,
        KANNADA,
        BENGALI,
        GURMUKHI,
        GUJARATI,
        ODIA,
        URDU_ARABIC,
        KOREAN,
        JAPANESE,
        OTHER
    }

    /**
     * Detects the dominant non-Latin script of a word or token.
     */
    fun detectScript(text: String?): DetectedScript {
        if (text.isNullOrEmpty()) return DetectedScript.LATIN
        for (ch in text) {
            val code = ch.code
            when {
                code in 0x0900..0x097F || code in 0xA8E0..0xA8FF -> return DetectedScript.DEVANAGARI
                code in 0x0C00..0x0C7F -> return DetectedScript.TELUGU
                code in 0x0B80..0x0BFF -> return DetectedScript.TAMIL
                code in 0x0D00..0x0D7F -> return DetectedScript.MALAYALAM
                code in 0x0C80..0x0CFF -> return DetectedScript.KANNADA
                code in 0x0980..0x09FF -> return DetectedScript.BENGALI
                code in 0x0A00..0x0A7F -> return DetectedScript.GURMUKHI
                code in 0x0A80..0x0AFF -> return DetectedScript.GUJARATI
                code in 0x0B00..0x0B7F -> return DetectedScript.ODIA
                code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0xFB50..0xFEFF -> return DetectedScript.URDU_ARABIC
                code in 0xAC00..0xD7A3 || code in 0x1100..0x11FF || code in 0x3130..0x318F -> return DetectedScript.KOREAN
                code in 0x3040..0x30FF -> return DetectedScript.JAPANESE
                code > 0x024F && ch.isLetter() -> return DetectedScript.OTHER
            }
        }
        return DetectedScript.LATIN
    }

    /**
     * Returns true if [text] contains any non-Latin script character that can be Romanized.
     */
    fun containsNonLatinScript(text: String?): Boolean {
        return detectScript(text) != DetectedScript.LATIN
    }

    // =========================================================================
    // Curated Language-Specific Lyric Lexicons
    // =========================================================================

    private val teluguLexicon: Map<String, String> = mapOf(
        "నువ్వే" to "nuvve",
        "నువ్వు" to "nuvvu",
        "నా" to "naa",
        "నీ" to "nee",
        "నేను" to "nenu",
        "నాకు" to "naaku",
        "నీకు" to "neeku",
        "ప్రాణం" to "pranam",
        "కోసమే" to "kosame",
        "కోసం" to "kosam",
        "ప్రేమ" to "prema",
        "మనసు" to "manasu",
        "హృదయం" to "hrudayam",
        "కన్నుల" to "kannula",
        "కళ్ళు" to "kallu",
        "కల" to "kala",
        "కలలు" to "kalalu",
        "పాట" to "paata",
        "మధురమైన" to "madhuramaina",
        "చెలియా" to "cheliya",
        "చెలి" to "cheli",
        "ప్రియా" to "priya",
        "నింగి" to "ningi",
        "నేల" to "nela",
        "గాలి" to "gaali",
        "వెన్నెల" to "vennela",
        "జాబిలి" to "jaabili",
        "చినుకు" to "chinuku",
        "వాన" to "vaana",
        "లోకం" to "lokam",
        "కాలం" to "kaalam",
        "జీవితం" to "jeevitham",
        "సంతోషం" to "santhosham",
        "ఆనందం" to "aanandam",
        "అందం" to "andam",
        "అందమైన" to "andamaina",
        "మౌనం" to "mounam",
        "రాగం" to "raagam",
        "తాళం" to "thaalam",
        "గానం" to "gaanam",
        "నిజం" to "nijam",
        "మనం" to "manam",
        "ఎందుకు" to "enduku",
        "ఎలా" to "ela",
        "ఎక్కడ" to "ekkada",
        "ఎప్పుడు" to "eppudu",
        "ఇలా" to "ila",
        "అలా" to "ala",
        "చాలు" to "chaalu",
        "రా" to "raa",
        "రావే" to "raave",
        "పో" to "po",
        "ఉంది" to "undi",
        "లేదు" to "ledu",
        "కాదు" to "kaadu"
    )

    private val tamilLexicon: Map<String, String> = mapOf(
        "நீ" to "nee",
        "நான்" to "naan",
        "என்" to "en",
        "உன்" to "un",
        "காதல்" to "kaadhal",
        "காதலே" to "kaadhale",
        "உயிரே" to "uyire",
        "உயிர்" to "uyir",
        "கண்ணே" to "kanne",
        "கண்மணி" to "kanmani",
        "அன்பே" to "anbe",
        "அன்பு" to "anbu",
        "இதயம்" to "idhayam",
        "நெஞ்சம்" to "nenjam",
        "நெஞ்சில்" to "nenjil",
        "மனம்" to "manam",
        "மனதில்" to "manadhil",
        "கனவு" to "kanavu",
        "கனவில்" to "kanavil",
        "வானம்" to "vaanam",
        "நிலா" to "nila",
        "நிலவே" to "nilave",
        "காற்று" to "kaatru",
        "மழை" to "mazhai",
        "பூவே" to "poove",
        "பூக்கள்" to "pookkal",
        "பாடல்" to "paadal",
        "இசை" to "isai",
        "ராகம்" to "raagam",
        "தாளம்" to "thaalam",
        "வாழ்க்கை" to "vaazhkkai",
        "உலகம்" to "ulagam",
        "அழகு" to "azhagu",
        "அழகிய" to "azhagiya",
        "துணை" to "thunai",
        "பார்வை" to "paarvai",
        "மொழி" to "mozhi",
        "வழி" to "vazhi",
        "விழி" to "vizhi",
        "இன்று" to "indru",
        "என்றும்" to "endrum",
        "எந்தன்" to "endhan",
        "உந்தன்" to "undhan"
    )

    private val malayalamLexicon: Map<String, String> = mapOf(
        "നീ" to "nee",
        "ഞാൻ" to "njaan",
        "എന്റെ" to "ente",
        "നിന്റെ" to "ninte",
        "തന്റെ" to "thante",
        "പ്രണയം" to "pranayam",
        "ഹൃദയം" to "hridayam",
        "മനസ്സ്" to "manassu",
        "മനസ്സിൽ" to "manassil",
        "സ്നേഹം" to "sneham",
        "യിരমে" to "uyire",
        "ഉയിരേ" to "uyire",
        "ഉയിർ" to "uyir",
        "കണ്ണേ" to "kanne",
        "കണ്ണുകൾ" to "kannukal",
        "കനവ്" to "kanavu",
        "കിനാവ്" to "kinaavu",
        "രാത്രി" to "raathri",
        "நிலാവ്" to "nilaavu",
        "നിലാവ്" to "nilaavu",
        "മഴ" to "mazha",
        "കാറ്റ്" to "kaattu",
        "പൂവ്" to "poovu",
        "പാട്ട്" to "paattu",
        "ഗാനം" to "gaanam",
        "രാഗം" to "raagam",
        "ജീവിതം" to "jeevitham",
        "ലോകം" to "lokam",
        "അഴക്" to "azhaku",
        "മൗനം" to "mounam",
        "കൂടെ" to "koode",
        "ഇവിടെ" to "ivide",
        "എവിടെ" to "evide",
        "എന്നും" to "ennum"
    )

    private val kannadaLexicon: Map<String, String> = mapOf(
        "ನೀನೇ" to "neene",
        "ನೀನು" to "neenu",
        "ನಾನು" to "naanu",
        "ನನ್ನ" to "nanna",
        "ನಿನ್ನ" to "ninna",
        "ಪ್ರೀತಿ" to "preethi",
        "ಪ್ರೀತಿಯ" to "preethiya",
        "ಮನಸ್ಸು" to "manassu",
        "ಮನಸೇ" to "manase",
        "ಹೃದಯ" to "hrudaya",
        "ಕಣ್ಣು" to "kannu",
        "ಕನಸು" to "kanasu",
        "ಹಾಡು" to "haadu",
        "ಸಂಗೀತ" to "sangeetha",
        "ಜೀವ" to "jeeva",
        "ಜೀವನ" to "jeevana",
        "ಪ್ರಾಣ" to "praana",
        "ಬಾಳು" to "baalu",
        "ಲೋಕ" to "loka",
        "ಮುಗಿಲು" to "mugilu",
        "ಮಳೆ" to "male",
        "ಗಾಳಿ" to "gaali",
        "ಬೆಳದಿಂಗಳು" to "beladingalu",
        "ಹೂವು" to "hoovu",
        "ಚೆಲುವೆ" to "cheluve",
        "ಗೆಳೆಯ" to "geleya",
        "ಗೆಳತಿ" to "gelathi",
        "ಜೊತೆ" to "jothe",
        "ಎಂದೆಂದೂ" to "endendoo"
    )

    private val bengaliLexicon: Map<String, String> = mapOf(
        "তুমি" to "tumi",
        "আমি" to "ami",
        "আমার" to "amar",
        "তোমার" to "tomar",
        "তোমাকে" to "tomake",
        "আমাকে" to "amake",
        "ভালোবাসা" to "bhalobasha",
        "ভালোবাসি" to "bhalobashi",
        "মন" to "mon",
        "মনের" to "moner",
        "হৃদয়" to "hridoy",
        "প্রাণ" to "pran",
        "চোখ" to "chokh",
        "চোখে" to "chokhe",
        "স্বপ্ন" to "shopno",
        "গান" to "gaan",
        "সুর" to "shur",
        "আকাশ" to "akash",
        "বাতাস" to "batash",
        "বৃষ্টি" to "brishti",
        "মেঘ" to "megh",
        "চাঁদ" to "chaad",
        "রাত" to "raat",
        "দিন" to "din",
        "আলো" to "alo",
        "ছায়া" to "chhaya",
        "জীবন" to "jeebon",
        "পৃথিবী" to "prithibi",
        "সাথে" to "shathe",
        "কাছে" to "kachhe",
        "দূরে" to "doore",
        "কেন" to "keno",
        "কিছু" to "kichhu",
        "সব" to "shob",
        "শুধু" to "shudhu"
    )

    private val gurmukhiLexicon: Map<String, String> = mapOf(
        "ਤੂੰ" to "tu",
        "ਮੈਂ" to "main",
        "ਮੇਰਾ" to "mera",
        "ਮੇਰੀ" to "meri",
        "ਮੇਰੇ" to "mere",
        "ਤੇਰਾ" to "tera",
        "ਤੇਰੀ" to "teri",
        "ਤੇਰੇ" to "tere",
        "Menu" to "mainu",
        "ਮੈਨੂੰ" to "mainu",
        "ਤੈਨੂੰ" to "tainu",
        "ਸਾਡਾ" to "saada",
        "ਸਾਡੀ" to "saadi",
        "ਦਿਲ" to "dil",
        "ਪਿਆਰ" to "pyaar",
        "ਇਸ਼ਕ" to "ishq",
        "ਇਸ਼ਕ਼" to "ishq",
        "ਮੁਹੱਬਤ" to "mohabbat",
        "ਯਾਰ" to "yaar",
        "ਯਾਰਾ" to "yaara",
        "ਸੱਜਣ" to "sajjan",
        "ਸੱਜਣਾ" to "sajjna",
        "ਮਾਹੀ" to "maahi",
        "ਸੋਹਣਾ" to "sohna",
        "ਸੋਹਣੀ" to "sohni",
        "ਚੰਨਾ" to "channa",
        "ਰੱਬ" to "rabb",
        "ਖੁਦਾ" to "khuda",
        "ਜ਼ਿੰਦਗੀ" to "zindagi",
        "ਜਿੰਦਗੀ" to "zindagi",
        "ਜਾਨ" to "jaan",
        "ਸਾਹ" to "saah",
        "ਅੱਖੀਆਂ" to "akhiyaan",
        "ਅੱਖਾਂ" to "akhaan",
        "ਸੁਪਨਾ" to "supna",
        "ਸੁਪਨੇ" to "supne",
        "ਰਾਤ" to "raat",
        "ਗੱਲ" to "gall",
        "ਗੱਲਾਂ" to "gallaan",
        "ਦਿਆਂ" to "diyaan",
        "ਦੀਆਂ" to "diyaan",
        "ਨਾਲ" to "naal",
        "ਬਿਨਾਂ" to "bina",
        "ਹੈ" to "hai",
        "ਹਾਂ" to "haan",
        "ਨਹੀਂ" to "nahi"
    )

    private val gujaratiLexicon: Map<String, String> = mapOf(
        "તું" to "tu",
        "હું" to "hu",
        "મારો" to "maaro",
        "મારી" to "maari",
        "મારા" to "maara",
        "તારો" to "taaro",
        "તારી" to "taari",
        "તારા" to "taara",
        "પ્રેમ" to "prem",
        "દિલ" to "dil",
        "મન" to "man",
        "જીવન" to "jeevan",
        "આંખ" to "aankh",
        "સપના" to "sapna",
        "ગીત" to "geet",
        "રાત" to "raat",
        "સાથ" to "saath",
        "છે" to "chhe",
        "નથી" to "nathi"
    )

    private val odiaLexicon: Map<String, String> = mapOf(
        "ତୁମେ" to "tume",
        "ତু" to "tu",
        "ତୁ" to "tu",
        "ମୁଁ" to "mun",
        "ମୋ" to "mo",
        "ମୋର" to "mora",
        "ତୋ" to "to",
        "ତୋର" to "tora",
        "ପ୍ରେମ" to "prema",
        "ମନ" to "mana",
        "ହୃଦୟ" to "hrudaya",
        "ଜୀବନ" to "jeebana",
        "ଆଖି" to "aakhi",
        "ସପନ" to "sapana",
        "ଗୀତ" to "geeta"
    )

    private val urduLexicon: Map<String, String> = mapOf(
        "تم" to "tum",
        "تو" to "tu",
        "میں" to "main",
        "ہم" to "hum",
        "آپ" to "aap",
        "وہ" to "woh",
        "یہ" to "yeh",
        "ہی" to "hi",
        "بھی" to "bhi",
        "ہو" to "ho",
        "ہے" to "hai",
        "ہیں" to "hain",
        "ہوں" to "hoon",
        "تھا" to "tha",
        "تھی" to "thi",
        "تھے" to "the",
        "اب" to "ab",
        "کب" to "kab",
        "جب" to "jab",
        "تب" to "tab",
        "سب" to "sab",
        "رب" to "rab",
        "میرا" to "mera",
        "میری" to "meri",
        "میرے" to "mere",
        "مجھے" to "mujhe",
        "مجھ" to "mujh",
        "تیرا" to "tera",
        "تیری" to "teri",
        "تیرے" to "tere",
        "تجھے" to "tujhe",
        "تجھ" to "tujh",
        "تمہارا" to "tumhaara",
        "تمہاری" to "tumhaari",
        "تمہارے" to "tumhaare",
        "ہمارا" to "hamaara",
        "ہماری" to "hamaari",
        "ہمارے" to "hamaare",
        "دل" to "dil",
        "جان" to "jaan",
        "پیار" to "pyaar",
        "عشق" to "ishq",
        "محبت" to "mohabbat",
        "زندگی" to "zindagi",
        "عاشقی" to "aashiqui",
        "عاشق" to "aashiq",
        "صنم" to "sanam",
        "قسم" to "kasam",
        "خدا" to "khuda",
        "دعا" to "dua",
        "وفا" to "wafa",
        "بےوفا" to "bewafa",
        "جدا" to "juda",
        "جدائی" to "judaai",
        "تنہا" to "tanha",
        "تنہائی" to "tanhaai",
        "ہوتا" to "hota",
        "ہوتی" to "hoti",
        "ہوتے" to "hote",
        "بنا" to "bina",
        "بن" to "bin",
        "ساتھ" to "saath",
        "پاس" to "paas",
        "دور" to "door",
        "اور" to "aur",
        "نہیں" to "nahi",
        "نہ" to "na",
        "نا" to "na",
        "ہاں" to "haan",
        "کیا" to "kya",
        "کیوں" to "kyun",
        "کیسے" to "kaise",
        "کہاں" to "kahaan",
        "یہاں" to "yahaan",
        "وہاں" to "wahaan",
        "جہاں" to "jahaan",
        "خواب" to "khwaab",
        "شام" to "shaam",
        "رات" to "raat",
        "بات" to "baat",
        "یاد" to "yaad",
        "آنکھ" to "aankh",
        "آنکھیں" to "aankhein",
        "آنکھوں" to "aankhon",
        "سانس" to "saans",
        "سانسیں" to "saansein",
        "راہ" to "raah",
        "منزل" to "manzil",
        "سفر" to "safar",
        "نظر" to "nazar",
        "چاند" to "chaand",
        "ستارے" to "sitaare",
        "آسمان" to "aasmaan",
        "ہوا" to "hawa",
        "خوشبو" to "khushboo",
        "پھول" to "phool",
        "نور" to "noor",
        "سکون" to "sukoon",
        "جنون" to "junoon",
        "درد" to "dard",
        "غم" to "gham"
    )

    // =========================================================================
    // Line, Word & Syllable Transliteration API
    // =========================================================================

    /**
     * Converts a [LyricLine] (and all its timed words and syllables) into Romanized English letters
     * while preserving every `startMs` and `endMs` timestamp and recalculating character spans.
     */
    fun toRomanizedLyricLine(line: LyricLine): LyricLine {
        val officialRoman = line.romanizedText?.trim()
            ?.takeIf { it.isNotEmpty() && !containsNonLatinScript(it) }

        val mainHasNonLatin = containsNonLatinScript(line.text) ||
            line.words.any { containsNonLatinScript(it.text) }
        val bgHasNonLatin = containsNonLatinScript(line.backgroundVocalText) ||
            line.backgroundVocalWords.any { containsNonLatinScript(it.text) }

        if (!mainHasNonLatin && !bgHasNonLatin && officialRoman == null) {
            return line
        }

        // Fast-path for Devanagari (Hindi / Marathi / Bhojpuri / Sanskrit)
        if (HindiRomanTransliterator.containsDevanagari(line.text) ||
            line.words.any { HindiRomanTransliterator.containsDevanagari(it.text) }
        ) {
            return HindiRomanTransliterator.transliterateLyricLine(line, preferRomanized = true)
        }

        var updatedText = line.text
        var updatedWords = line.words

        if (mainHasNonLatin || officialRoman != null) {
            if (line.words.isNotEmpty()) {
                val officialTokens = officialRoman
                    ?.split(Regex("""\s+"""))
                    ?.filter { it.isNotEmpty() }

                if (officialTokens != null && officialTokens.size == line.words.size) {
                    val rebuilt = mapOfficialRomanTokensToTimedWords(line.words, officialTokens)
                    updatedText = rebuilt.first
                    updatedWords = rebuilt.second
                } else if (mainHasNonLatin) {
                    val rebuilt = transliterateTimedWordsMultilingual(line.words)
                    updatedText = rebuilt.first
                    updatedWords = rebuilt.second
                } else if (officialRoman != null) {
                    updatedText = officialRoman
                }
            } else {
                updatedText = officialRoman ?: transliterateLine(line.text)
            }
        }

        var updatedBgText = line.backgroundVocalText
        var updatedBgWords = line.backgroundVocalWords
        if (bgHasNonLatin) {
            if (line.backgroundVocalWords.isNotEmpty()) {
                val rebuiltBg = transliterateTimedWordsMultilingual(line.backgroundVocalWords)
                updatedBgText = rebuiltBg.first
                updatedBgWords = rebuiltBg.second
            } else if (!line.backgroundVocalText.isNullOrBlank()) {
                updatedBgText = transliterateLine(line.backgroundVocalText)
            }
        }

        return line.copy(
            text = updatedText,
            words = updatedWords,
            backgroundVocalText = updatedBgText,
            backgroundVocalWords = updatedBgWords,
            romanizedText = null
        )
    }

    /**
     * Transliterates any supported language line into Romanized English letters,
     * preserving word order, punctuation, and mixed-language tokens.
     */
    fun transliterateLine(line: String): String {
        if (line.isBlank() || !containsNonLatinScript(line)) return line
        if (HindiRomanTransliterator.containsDevanagari(line) &&
            detectScript(line) == DetectedScript.DEVANAGARI
        ) {
            return HindiRomanTransliterator.transliterateLine(line)
        }

        val tokenRegex = Regex("""(\s+)|([^\s]+)""")
        val matches = tokenRegex.findAll(line).toList()
        val sb = StringBuilder()
        var isFirstWord = true

        for (m in matches) {
            val ws = m.groupValues[1]
            if (ws.isNotEmpty()) {
                sb.append(ws)
                continue
            }
            val token = m.groupValues[2]
            if (containsNonLatinScript(token)) {
                val rom = transliterateWord(token, capitalizeFirst = isFirstWord)
                sb.append(rom)
                if (rom.any { it.isLetterOrDigit() }) {
                    isFirstWord = false
                }
            } else {
                sb.append(token)
                if (token.any { it.isLetterOrDigit() }) {
                    isFirstWord = false
                }
            }
        }
        return sb.toString().trim()
    }

    /**
     * Transliterates a single word token in any supported script into Romanized English letters.
     */
    fun transliterateWord(rawWord: String, capitalizeFirst: Boolean = false): String {
        if (rawWord.isEmpty() || !containsNonLatinScript(rawWord)) return rawWord

        val script = detectScript(rawWord)
        if (script == DetectedScript.DEVANAGARI) {
            return HindiRomanTransliterator.transliterateWord(rawWord, capitalizeFirst)
        }

        val (leadPunct, core, trailPunct) = splitPunctuation(rawWord)
        if (core.isEmpty()) return rawWord

        val romCore = when (script) {
            DetectedScript.TELUGU -> transliterateTeluguWord(core)
            DetectedScript.TAMIL -> transliterateTamilWord(core)
            DetectedScript.MALAYALAM -> transliterateMalayalamWord(core)
            DetectedScript.KANNADA -> transliterateKannadaWord(core)
            DetectedScript.BENGALI -> transliterateBengaliWord(core)
            DetectedScript.GURMUKHI -> transliterateGurmukhiWord(core)
            DetectedScript.GUJARATI -> transliterateGujaratiWord(core)
            DetectedScript.ODIA -> transliterateOdiaWord(core)
            DetectedScript.URDU_ARABIC -> transliterateUrduWord(core)
            DetectedScript.KOREAN -> transliterateKoreanWord(core)
            DetectedScript.JAPANESE -> transliterateJapaneseWord(core)
            DetectedScript.OTHER -> transliterateIcuFallback(core)
            else -> core
        }.ifEmpty { core }

        val cased = if (capitalizeFirst && romCore.isNotEmpty()) {
            romCore.replaceFirstChar { it.uppercaseChar() }
        } else {
            romCore
        }
        return "$leadPunct$cased$trailPunct"
    }

    private fun transliterateTimedWordsMultilingual(words: List<LyricWord>): Pair<String, List<LyricWord>> {
        if (words.isEmpty()) return "" to emptyList()

        val lineBuilder = StringBuilder()
        val newWords = ArrayList<LyricWord>(words.size)

        for ((wIdx, word) in words.withIndex()) {
            val wordStartInLine = if (lineBuilder.isEmpty()) 0 else {
                lineBuilder.append(" ")
                lineBuilder.length
            }
            val shouldCap = (wIdx == 0)

            if (word.syllables.size <= 1) {
                val romWord = if (containsNonLatinScript(word.text)) {
                    transliterateWord(word.text, capitalizeFirst = shouldCap)
                } else {
                    word.text
                }
                lineBuilder.append(romWord)
                val wEndInLine = lineBuilder.length
                val updatedSyllables = if (word.syllables.size == 1) {
                    val s0 = word.syllables[0]
                    listOf(
                        s0.copy(
                            text = romWord,
                            charStartInWord = 0,
                            charEndInWord = romWord.length,
                            charStartInLine = wordStartInLine,
                            charEndInLine = wEndInLine
                        )
                    )
                } else {
                    emptyList()
                }
                newWords.add(
                    word.copy(
                        text = romWord,
                        index = wIdx,
                        charStart = wordStartInLine,
                        charEnd = wEndInLine,
                        syllables = updatedSyllables
                    )
                )
            } else {
                // Multi-syllable word
                val wholeRomanWord = if (containsNonLatinScript(word.text)) {
                    transliterateWord(word.text, capitalizeFirst = shouldCap)
                } else {
                    word.text
                }
                val sylTexts = word.syllables.mapIndexed { sIdx, syl ->
                    if (containsNonLatinScript(syl.text)) {
                        transliterateWord(syl.text, capitalizeFirst = (shouldCap && sIdx == 0))
                    } else {
                        syl.text
                    }
                }.toMutableList()

                if (wholeRomanWord.isNotEmpty() && wholeRomanWord != sylTexts.joinToString("")) {
                    alignSyllableTextsToWholeWord(sylTexts, wholeRomanWord)
                }

                val wordBuilder = StringBuilder()
                val newSyllables = ArrayList<LyricSyllable>(word.syllables.size)
                for ((sIdx, origSyl) in word.syllables.withIndex()) {
                    val sText = sylTexts[sIdx]
                    val cStartInWord = wordBuilder.length
                    wordBuilder.append(sText)
                    val cEndInWord = wordBuilder.length
                    newSyllables.add(
                        origSyl.copy(
                            text = sText,
                            index = sIdx,
                            charStartInWord = cStartInWord,
                            charEndInWord = cEndInWord,
                            charStartInLine = wordStartInLine + cStartInWord,
                            charEndInLine = wordStartInLine + cEndInWord
                        )
                    )
                }

                val finalWordText = wordBuilder.toString()
                lineBuilder.append(finalWordText)
                val wEndInLine = lineBuilder.length
                newWords.add(
                    word.copy(
                        text = finalWordText,
                        index = wIdx,
                        charStart = wordStartInLine,
                        charEnd = wEndInLine,
                        syllables = newSyllables
                    )
                )
            }
        }
        return lineBuilder.toString() to newWords
    }

    private fun mapOfficialRomanTokensToTimedWords(
        words: List<LyricWord>,
        romanTokens: List<String>
    ): Pair<String, List<LyricWord>> {
        val lineBuilder = StringBuilder()
        val newWords = ArrayList<LyricWord>(words.size)

        for ((wIdx, word) in words.withIndex()) {
            val romToken = romanTokens[wIdx]
            val wordStartInLine = if (lineBuilder.isEmpty()) 0 else {
                lineBuilder.append(" ")
                lineBuilder.length
            }

            if (word.syllables.size <= 1) {
                lineBuilder.append(romToken)
                val wEndInLine = lineBuilder.length
                val updatedSyllables = if (word.syllables.size == 1) {
                    val s0 = word.syllables[0]
                    listOf(
                        s0.copy(
                            text = romToken,
                            charStartInWord = 0,
                            charEndInWord = romToken.length,
                            charStartInLine = wordStartInLine,
                            charEndInLine = wEndInLine
                        )
                    )
                } else {
                    emptyList()
                }
                newWords.add(
                    word.copy(
                        text = romToken,
                        index = wIdx,
                        charStart = wordStartInLine,
                        charEnd = wEndInLine,
                        syllables = updatedSyllables
                    )
                )
            } else {
                val sylTexts = word.syllables.mapIndexed { sIdx, syl ->
                    transliterateWord(syl.text, capitalizeFirst = (wIdx == 0 && sIdx == 0))
                }.toMutableList()
                alignSyllableTextsToWholeWord(sylTexts, romToken)

                val wordBuilder = StringBuilder()
                val newSyllables = ArrayList<LyricSyllable>(word.syllables.size)
                for ((sIdx, origSyl) in word.syllables.withIndex()) {
                    val sText = sylTexts[sIdx]
                    val cStartInWord = wordBuilder.length
                    wordBuilder.append(sText)
                    val cEndInWord = wordBuilder.length
                    newSyllables.add(
                        origSyl.copy(
                            text = sText,
                            index = sIdx,
                            charStartInWord = cStartInWord,
                            charEndInWord = cEndInWord,
                            charStartInLine = wordStartInLine + cStartInWord,
                            charEndInLine = wordStartInLine + cEndInWord
                        )
                    )
                }
                val finalWordText = wordBuilder.toString()
                lineBuilder.append(finalWordText)
                val wEndInLine = lineBuilder.length
                newWords.add(
                    word.copy(
                        text = finalWordText,
                        index = wIdx,
                        charStart = wordStartInLine,
                        charEnd = wEndInLine,
                        syllables = newSyllables
                    )
                )
            }
        }
        return lineBuilder.toString() to newWords
    }

    private fun alignSyllableTextsToWholeWord(sylTexts: MutableList<String>, targetWord: String) {
        if (sylTexts.isEmpty() || targetWord.isEmpty()) return
        if (sylTexts.size == 1) {
            sylTexts[0] = targetWord
            return
        }
        val totalEst = sylTexts.sumOf { it.length.coerceAtLeast(1) }.toFloat()
        var cursor = 0
        for (i in sylTexts.indices) {
            if (i == sylTexts.lastIndex) {
                sylTexts[i] = targetWord.substring(cursor.coerceAtMost(targetWord.length))
            } else {
                val frac = sylTexts[i].length.coerceAtLeast(1) / totalEst
                val remainingSyls = sylTexts.size - 1 - i
                val maxEnd = (targetWord.length - remainingSyls).coerceAtLeast(cursor)
                val take = (targetWord.length * frac).toInt().coerceAtLeast(1)
                val nextCursor = (cursor + take).coerceIn(cursor, maxEnd)
                sylTexts[i] = targetWord.substring(cursor, nextCursor)
                cursor = nextCursor
            }
        }
    }

    // =========================================================================
    // Language-Specific Phonetic Engines
    // =========================================================================

    private fun transliterateTeluguWord(core: String): String {
        teluguLexicon[core]?.let { return it }
        return transliterateDravidianBrahmic(
            core = core,
            blockBase = 0x0C00,
            dropWordFinalSchwa = false,
            intervocalicDentalAsDh = false
        )
    }

    private fun transliterateKannadaWord(core: String): String {
        kannadaLexicon[core]?.let { return it }
        return transliterateDravidianBrahmic(
            core = core,
            blockBase = 0x0C80,
            dropWordFinalSchwa = false,
            intervocalicDentalAsDh = true
        )
    }

    private fun transliterateMalayalamWord(core: String): String {
        malayalamLexicon[core]?.let { return it }
        // Handle Malayalam chillu letters and special ന്റെ ("nte") conjunct before Brahmic pass
        val preProcessed = core
            .replace("ന്റെ", "nte")
            .replace("\u0D28\u0D4D\u0D31\u0D46", "nte")
            .replace("ന്റ", "nta")
            .replace("\u0D7A", "n") // ൺ
            .replace("\u0D7B", "n") // ൻ
            .replace("\u0D7C", "r") // ർ
            .replace("\u0D7D", "l") // ൽ
            .replace("\u0D7E", "l") // ൾ
            .replace("\u0D7F", "k") // ൿ
            .replace("ങ്ക", "nka")
            .replace("ഞ്ച", "ncha")
            .replace("ണ്ട", "nda")
            .replace("ന്ത", "ntha")
            .replace("മ്പ", "mpa")
            .replace("ങ്ങ", "nga")
            .replace("ഞ്ഞ", "nja")
            .replace("ണ്ണ", "nna")
            .replace("ന്ന", "nna")
            .replace("മ്മ", "mma")
            .replace("ല്ല", "lla")
            .replace("ള്ള", "lla")
            .replace("വ്വ", "vva")
            .replace("യ്യ", "yya")
            .replace("റ്റ", "tta")
        return transliterateDravidianBrahmic(
            core = preProcessed,
            blockBase = 0x0D00,
            dropWordFinalSchwa = false,
            intervocalicDentalAsDh = false
        )
    }

    private fun transliterateTamilWord(core: String): String {
        tamilLexicon[core]?.let { return it }
        val sb = StringBuilder()
        var i = 0
        val len = core.length

        while (i < len) {
            val ch = core[i]
            val offset = ch.code - 0x0B80
            if (offset !in 0x00..0x7F) {
                sb.append(ch)
                i++
                continue
            }

            // Independent Tamil vowels (0x05..0x14)
            if (offset in 0x05..0x14) {
                val v = when (offset) {
                    0x05 -> "a"   // அ
                    0x06 -> "aa"  // ஆ
                    0x07 -> "i"   // இ
                    0x08 -> "ee"  // ஈ
                    0x09 -> "u"   // உ
                    0x0A -> "oo"  // ஊ
                    0x0E -> "e"   // எ
                    0x0F -> "e"   // ஏ
                    0x10 -> "ai"  // ஐ
                    0x12 -> "o"   // ஒ
                    0x13 -> "o"   // ஓ
                    0x14 -> "au"  // ஒள
                    else -> "a"
                }
                sb.append(v)
                i++
                continue
            }

            // Tamil consonants (0x15..0x39)
            if (offset in 0x15..0x39) {
                val prevChar = if (i > 0) core[i - 1] else '\u0000'
                val prevHasPulli = (prevChar.code - 0x0B80) == 0x4D
                val prevConsonantChar = if (prevHasPulli && i >= 2) core[i - 2] else '\u0000'
                val isWordInitial = (i == 0)
                val isGeminated = prevHasPulli && prevConsonantChar == ch
                val isAfterNasal = prevHasPulli && (
                    prevConsonantChar == 'ங' || prevConsonantChar == 'ஞ' ||
                        prevConsonantChar == 'ண' || prevConsonantChar == 'ந' ||
                        prevConsonantChar == 'ம' || prevConsonantChar == 'ன'
                    )
                val isIntervocalic = !isWordInitial && !prevHasPulli

                val cRoman = when (ch) {
                    'க' -> if (isWordInitial || isGeminated) "k" else if (isAfterNasal) "g" else "g"
                    'ங' -> "ng"
                    'ச' -> if (isGeminated) "ch" else if (isAfterNasal) "j" else "s"
                    'ஞ' -> "nj"
                    'ட' -> if (isWordInitial || isGeminated) "t" else "d"
                    'ண' -> "n"
                    'த' -> if (isWordInitial || isGeminated) "th" else if (isIntervocalic || isAfterNasal) "dh" else "th"
                    'ந', 'ன' -> "n"
                    'ப' -> if (isWordInitial || isGeminated) "p" else "b"
                    'ம' -> "m"
                    'ய' -> "y"
                    'ர', 'ற' -> if (ch == 'ற' && isGeminated) "tr" else if (ch == 'ற' && isAfterNasal) "dr" else "r"
                    'ல', 'ள' -> "l"
                    'ழ' -> "zh"
                    'வ' -> "v"
                    'ஶ', 'ஷ' -> "sh"
                    'ஸ' -> "s"
                    'ஹ' -> "h"
                    else -> ""
                }
                sb.append(cRoman)
                i++

                var hasPulli = false
                var matraOffset = -1
                while (i < len) {
                    val mOff = core[i].code - 0x0B80
                    if (mOff == 0x4D) { // ் pulli
                        hasPulli = true
                        i++
                    } else if (mOff in 0x3E..0x4C || mOff == 0x57) {
                        matraOffset = mOff
                        i++
                    } else {
                        break
                    }
                }

                if (!hasPulli) {
                    if (matraOffset != -1) {
                        val isAtEnd = (i >= len)
                        val vStr = when (matraOffset) {
                            0x3E -> if (isAtEnd && core.length > 2) "a" else "aa" // ா
                            0x3F -> "i"  // ி
                            0x40 -> if (isAtEnd && core.length <= 2) "ee" else "i" // ீ
                            0x41 -> "u"  // ુ
                            0x42 -> "oo" // ூ
                            0x46 -> "e"  // ெ
                            0x47 -> "e"  // ே
                            0x48 -> "ai" // ை
                            0x4A -> "o"  // ொ
                            0x4B -> "o"  // ோ
                            0x4C, 0x57 -> "au" // ௌ
                            else -> "a"
                        }
                        sb.append(vStr)
                    } else {
                        sb.append("a")
                    }
                }
                continue
            }

            if (offset == 0x02) { // ஂ anusvara
                sb.append("m")
                i++
                continue
            }

            i++
        }
        return sb.toString()
    }

    private fun transliterateBengaliWord(core: String): String {
        bengaliLexicon[core]?.let { return it }
        return transliterateEasternOrWesternBrahmic(
            core = core,
            blockBase = 0x0980,
            dropWordFinalSchwa = true,
            isBengali = true
        )
    }

    private fun transliterateGurmukhiWord(core: String): String {
        gurmukhiLexicon[core]?.let { return it }
        return transliterateGurmukhiInternal(core)
    }

    private fun transliterateGujaratiWord(core: String): String {
        gujaratiLexicon[core]?.let { return it }
        return transliterateEasternOrWesternBrahmic(
            core = core,
            blockBase = 0x0A80,
            dropWordFinalSchwa = true,
            isBengali = false
        )
    }

    private fun transliterateOdiaWord(core: String): String {
        odiaLexicon[core]?.let { return it }
        return transliterateDravidianBrahmic(
            core = core,
            blockBase = 0x0B00,
            dropWordFinalSchwa = false,
            intervocalicDentalAsDh = false
        )
    }

    /**
     * Shared Dravidian / Southern Brahmic (Telugu 0x0C00, Kannada 0x0C80, Malayalam 0x0D00, Odia 0x0B00)
     * phonetic transliterator preserving inherent vowels and handling sunna/anusvara ('m' at word end).
     */
    private fun transliterateDravidianBrahmic(
        core: String,
        blockBase: Int,
        dropWordFinalSchwa: Boolean,
        intervocalicDentalAsDh: Boolean
    ): String {
        val sb = StringBuilder()
        var i = 0
        val len = core.length

        while (i < len) {
            val ch = core[i]
            val offset = ch.code - blockBase

            if (offset !in 0x00..0x7F) {
                sb.append(ch)
                i++
                continue
            }

            // Anusvara (+0x02) / Chandrabindu (+0x01)
            if (offset == 0x01 || offset == 0x02) {
                val nextOff = if (i + 1 < len) (core[i + 1].code - blockBase) else -1
                val isLabialOrEnd = nextOff == -1 || nextOff in 0x2A..0x2E || nextOff in 0x2F..0x39
                sb.append(if (isLabialOrEnd) "m" else "n")
                i++
                continue
            }
            if (offset == 0x03) {
                sb.append("h")
                i++
                continue
            }

            // Independent vowels (+0x05..+0x14, +0x60..+0x61)
            if (offset in 0x05..0x14 || offset in 0x60..0x61) {
                val v = when (offset) {
                    0x05 -> "a"
                    0x06 -> "aa"
                    0x07 -> "i"
                    0x08 -> "ee"
                    0x09 -> "u"
                    0x0A -> "oo"
                    0x0B, 0x60 -> "ru"
                    0x0E -> "e"
                    0x0F -> "e"
                    0x10 -> "ai"
                    0x12 -> "o"
                    0x13 -> "o"
                    0x14 -> "au"
                    else -> "a"
                }
                sb.append(v)
                i++
                continue
            }

            // Consonants (+0x15..+0x39)
            if (offset in 0x15..0x39) {
                val isWordInitial = (i == 0)
                val cRoman = mapBrahmicConsonantOffset(
                    offset = offset,
                    hasNukta = false,
                    isBengali = false,
                    intervocalicDentalAsDh = intervocalicDentalAsDh && !isWordInitial
                )
                sb.append(cRoman)
                i++

                if (i < len && (core[i].code - blockBase) == 0x3C) {
                    i++ // skip nukta if any
                }

                var hasVirama = false
                var matraOffset = -1
                while (i < len) {
                    val mOff = core[i].code - blockBase
                    if (mOff == 0x4D) {
                        hasVirama = true
                        i++
                    } else if (mOff in 0x3E..0x4C || mOff == 0x55 || mOff == 0x56 || mOff == 0x57) {
                        matraOffset = mOff
                        i++
                    } else {
                        break
                    }
                }

                if (!hasVirama) {
                    val isAtEnd = (i >= len) || (i == len - 1 && (core[i].code - blockBase) in 0x01..0x02)
                    if (matraOffset != -1) {
                        val vStr = when (matraOffset) {
                            0x3E -> if (core.length <= 2 && isAtEnd) "aa" else "a" // ా
                            0x3F -> "i"  // ి
                            0x40 -> if (core.length <= 3) "ee" else "i" // ీ
                            0x41 -> "u"  // ు
                            0x42 -> "oo" // ూ
                            0x43, 0x44 -> "ru"
                            0x46 -> "e"  // ె
                            0x47 -> "e"  // ే
                            0x48 -> "ai" // ై
                            0x4A -> "o"  // ొ
                            0x4B -> "o"  // ో
                            0x4C, 0x57 -> "au" // ౌ
                            else -> "a"
                        }
                        sb.append(vStr)
                    } else if (!dropWordFinalSchwa || !isAtEnd) {
                        sb.append("a")
                    }
                }
                continue
            }

            // Digits (+0x66..+0x6F)
            if (offset in 0x66..0x6F) {
                sb.append((offset - 0x66).toString())
                i++
                continue
            }

            i++
        }
        return sb.toString()
    }

    private fun transliterateEasternOrWesternBrahmic(
        core: String,
        blockBase: Int,
        dropWordFinalSchwa: Boolean,
        isBengali: Boolean
    ): String {
        val sb = StringBuilder()
        var i = 0
        val len = core.length

        while (i < len) {
            val ch = core[i]
            val offset = ch.code - blockBase
            if (offset !in 0x00..0x7F) {
                sb.append(ch)
                i++
                continue
            }

            if (offset == 0x01 || offset == 0x02) {
                sb.append(if (isBengali && offset == 0x02) "ng" else "n")
                i++
                continue
            }
            if (offset == 0x03) {
                sb.append("h")
                i++
                continue
            }

            if (offset in 0x05..0x14) {
                val v = when (offset) {
                    0x05 -> if (isBengali) "o" else "a"
                    0x06 -> "a"
                    0x07 -> "i"
                    0x08 -> "ee"
                    0x09 -> "u"
                    0x0A -> "oo"
                    0x0B -> "ri"
                    0x0F -> "e"
                    0x10 -> if (isBengali) "oi" else "ai"
                    0x13 -> "o"
                    0x14 -> if (isBengali) "ou" else "au"
                    else -> "a"
                }
                sb.append(v)
                i++
                continue
            }

            if (offset in 0x15..0x39 || offset in 0x5C..0x5F) {
                var hasNukta = false
                val baseOffset = when (offset) {
                    0x5C -> { hasNukta = true; 0x21 } // ড়
                    0x5D -> { hasNukta = true; 0x22 } // ঢ়
                    0x5F -> { hasNukta = true; 0x2F } // য়
                    else -> offset
                }
                i++
                if (i < len && (core[i].code - blockBase) == 0x3C) {
                    hasNukta = true
                    i++
                }

                val cRoman = if (isBengali && hasNukta && baseOffset == 0x2F) {
                    "y"
                } else {
                    mapBrahmicConsonantOffset(baseOffset, hasNukta, isBengali, false)
                }
                sb.append(cRoman)

                var hasVirama = false
                var matraOffset = -1
                while (i < len) {
                    val mOff = core[i].code - blockBase
                    if (mOff == 0x4D) {
                        hasVirama = true
                        i++
                    } else if (mOff in 0x3E..0x4C || mOff == 0x57) {
                        matraOffset = mOff
                        i++
                    } else {
                        break
                    }
                }

                if (!hasVirama) {
                    val isAtEnd = (i >= len)
                    if (matraOffset != -1) {
                        val vStr = when (matraOffset) {
                            0x3E -> "a"
                            0x3F -> "i"
                            0x40 -> "i"
                            0x41 -> "u"
                            0x42 -> "oo"
                            0x43 -> "ri"
                            0x47, 0x45 -> "e"
                            0x48 -> if (isBengali) "oi" else "ai"
                            0x4B, 0x49 -> "o"
                            0x4C, 0x57 -> if (isBengali) "ou" else "au"
                            else -> "a"
                        }
                        sb.append(vStr)
                    } else if (!dropWordFinalSchwa || !isAtEnd || core.length == 1) {
                        sb.append("a")
                    }
                }
                continue
            }

            i++
        }
        return sb.toString()
    }

    private fun transliterateGurmukhiInternal(core: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = core.length

        while (i < len) {
            val ch = core[i]
            val offset = ch.code - 0x0A00
            if (offset !in 0x00..0x7F) {
                sb.append(ch)
                i++
                continue
            }

            // Tippi (0x70 ੰ) or Bindi (0x02 ਂ)
            if (offset == 0x70 || offset == 0x02 || offset == 0x01) {
                val nextOff = if (i + 1 < len) (core[i + 1].code - 0x0A00) else -1
                val isLabial = nextOff in 0x2A..0x2E
                sb.append(if (isLabial) "m" else "n")
                i++
                continue
            }

            // Adhak (0x71 ੱ) - geminates next consonant, handled by consonant pass or ignored cleanly
            if (offset == 0x71) {
                i++
                continue
            }

            // Independent vowels (0x05..0x14) and Iri/Ura/Aira (0x72..0x74)
            if (offset in 0x05..0x14) {
                val v = when (offset) {
                    0x05 -> "a"
                    0x06 -> "aa"
                    0x07 -> "i"
                    0x08 -> "ee"
                    0x09 -> "u"
                    0x0A -> "oo"
                    0x0F -> "e"
                    0x10 -> "ai"
                    0x13 -> "o"
                    0x14 -> "au"
                    else -> "a"
                }
                sb.append(v)
                i++
                continue
            }

            if (offset in 0x15..0x39 || offset in 0x59..0x5E) {
                var hasNukta = false
                val baseOff = when (offset) {
                    0x59 -> { hasNukta = true; 0x16 } // ਖ਼
                    0x5A -> { hasNukta = true; 0x17 } // ਗ਼
                    0x5B -> { hasNukta = true; 0x1C } // ਜ਼
                    0x5C -> { hasNukta = true; 0x21 } // ੜ
                    0x5E -> { hasNukta = true; 0x2B } // ਫ਼
                    else -> offset
                }
                i++
                if (i < len && (core[i].code - 0x0A00) == 0x3C) {
                    hasNukta = true
                    i++
                }

                sb.append(mapBrahmicConsonantOffset(baseOff, hasNukta, isBengali = false, intervocalicDentalAsDh = false))

                var hasVirama = false
                var matraOffset = -1
                while (i < len) {
                    val mOff = core[i].code - 0x0A00
                    if (mOff == 0x4D) {
                        hasVirama = true
                        i++
                    } else if (mOff in 0x3E..0x4C) {
                        matraOffset = mOff
                        i++
                    } else {
                        break
                    }
                }

                if (!hasVirama) {
                    val isAtEnd = (i >= len)
                    if (matraOffset != -1) {
                        val vStr = when (matraOffset) {
                            0x3E -> if (isAtEnd) "a" else "aa"
                            0x3F -> "i"
                            0x40 -> "i"
                            0x41 -> "u"
                            0x42 -> if (isAtEnd && core.length <= 3) "u" else "oo"
                            0x47 -> "e"
                            0x48 -> "ai"
                            0x4B -> "o"
                            0x4C -> "au"
                            else -> "a"
                        }
                        sb.append(vStr)
                    } else if (!isAtEnd || core.length == 1) {
                        sb.append("a")
                    }
                }
                continue
            }

            i++
        }
        return sb.toString()
    }

    private fun mapBrahmicConsonantOffset(
        offset: Int,
        hasNukta: Boolean,
        isBengali: Boolean,
        intervocalicDentalAsDh: Boolean
    ): String = when (offset) {
        0x15 -> if (hasNukta) "q" else "k"
        0x16 -> "kh"
        0x17 -> if (hasNukta) "gh" else "g"
        0x18 -> "gh"
        0x19 -> "ng"
        0x1A -> "ch"
        0x1B -> "chh"
        0x1C -> if (hasNukta) "z" else "j"
        0x1D -> "jh"
        0x1E -> "ny"
        0x1F -> "t"
        0x20 -> "th"
        0x21 -> "d"
        0x22 -> "dh"
        0x23 -> "n"
        0x24 -> if (intervocalicDentalAsDh) "th" else "t"
        0x25 -> "th"
        0x26 -> "d"
        0x27 -> "dh"
        0x28, 0x29 -> "n"
        0x2A -> "p"
        0x2B -> if (hasNukta) "f" else "ph"
        0x2C -> "b"
        0x2D -> "bh"
        0x2E -> "m"
        0x2F -> if (isBengali && !hasNukta) "j" else "y"
        0x30, 0x31 -> "r"
        0x32, 0x33, 0x34 -> "l"
        0x35 -> "v"
        0x36, 0x37 -> "sh"
        0x38 -> if (isBengali) "sh" else "s"
        0x39 -> "h"
        else -> ""
    }

    // =========================================================================
    // Urdu / Arabic Transliteration
    // =========================================================================

    private fun transliterateUrduWord(core: String): String {
        val cleaned = core.replace("\u0640", "") // Strip tatweel
        urduLexicon[cleaned]?.let { return it }

        val sb = StringBuilder()
        var i = 0
        val len = cleaned.length

        while (i < len) {
            val ch = cleaned[i]
            val next = if (i + 1 < len) cleaned[i + 1] else '\u0000'
            val isInitial = (i == 0)
            val isFinal = (i == len - 1)

            // Handle aspirated consonant + do-chashmi he (ھ \u06BE)
            if (next == '\u06BE') {
                val asp = when (ch) {
                    'ب' -> "bh"
                    'پ' -> "ph"
                    'ت', 'ٹ' -> "th"
                    'ج' -> "jh"
                    'چ' -> "chh"
                    'د', 'ڈ' -> "dh"
                    'ڑ' -> "rh"
                    'ک', 'ك' -> "kh"
                    'گ' -> "gh"
                    else -> null
                }
                if (asp != null) {
                    sb.append(asp)
                    i += 2
                    if (i < len && !isUrduVowelLetter(cleaned[i])) {
                        sb.append("a")
                    }
                    continue
                }
            }

            val mapped = when (ch) {
                'آ' -> "aa"
                'ا', 'أ', 'إ' -> if (isInitial) "a" else "aa"
                'ب' -> "b"
                'پ' -> "p"
                'ت', 'ط', 'ٹ' -> "t"
                'ث', 'س', 'ص' -> "s"
                'ج' -> "j"
                'چ' -> "ch"
                'ح', 'ہ', 'ه', 'ھ' -> if (isFinal && i > 0) "a" else "h"
                'خ' -> "kh"
                'د', 'ڈ' -> "d"
                'ذ', 'ز', 'ض', 'ظ', 'ژ' -> "z"
                'ر', 'ڑ' -> "r"
                'ش' -> "sh"
                'ع' -> "a"
                'غ' -> "gh"
                'ف' -> "f"
                'ق' -> "q"
                'ک', 'ك' -> "k"
                'گ' -> "g"
                'ل' -> "l"
                'م' -> "m"
                'ن' -> "n"
                'ں' -> "n"
                'و', 'ؤ' -> if (isInitial) "w" else "o"
                'ی', 'ي', 'ئ', 'ى' -> if (isInitial) "y" else "i"
                'ے' -> "e"
                '\u064E' -> "a" // fatha
                '\u0650' -> "i" // kasra
                '\u064F' -> "u" // damma
                else -> ""
            }
            sb.append(mapped)

            // Insert short 'a' between consecutive consonants at start of word if no vowel letter follows
            if (isInitial && !isUrduVowelLetter(ch) && next != '\u0000' && !isUrduVowelLetter(next)) {
                sb.append("a")
            }
            i++
        }
        return sb.toString()
    }

    private fun isUrduVowelLetter(ch: Char): Boolean {
        return ch == 'ا' || ch == 'آ' || ch == 'و' || ch == 'ی' || ch == 'ي' ||
            ch == 'ے' || ch == '\u064E' || ch == '\u0650' || ch == '\u064F'
    }

    // =========================================================================
    // Korean (Hangul Revised Romanization) & Japanese (Hepburn Romaji)
    // =========================================================================

    private val hangulInitials = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s",
        "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    )
    private val hangulMedials = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
        "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    )
    private val hangulFinals = arrayOf(
        "", "k", "k", "k", "n", "n", "n", "t", "l", "k",
        "m", "p", "l", "l", "p", "l", "m", "p", "p", "t",
        "t", "ng", "t", "t", "k", "t", "p", "t"
    )

    private fun transliterateKoreanWord(core: String): String {
        val sb = StringBuilder()
        for (ch in core) {
            val code = ch.code
            if (code in 0xAC00..0xD7A3) {
                val sylIndex = code - 0xAC00
                val initialIdx = sylIndex / (21 * 28)
                val medialIdx = (sylIndex % (21 * 28)) / 28
                val finalIdx = sylIndex % 28
                sb.append(hangulInitials[initialIdx])
                sb.append(hangulMedials[medialIdx])
                sb.append(hangulFinals[finalIdx])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private val kanaRomajiMap: Map<Char, String> = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "o", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po"
    )

    private fun transliterateJapaneseWord(core: String): String {
        val sb = StringBuilder()
        var sokuon = false
        for (ch in core) {
            // Normalize Katakana (0x30A1..0x30F6) to Hiragana (0x3041..0x3096)
            val norm = if (ch.code in 0x30A1..0x30F6) (ch.code - 0x60).toChar() else ch
            if (norm == 'っ') {
                sokuon = true
                continue
            }
            val rom = kanaRomajiMap[norm]
            if (rom != null) {
                if (sokuon && rom.isNotEmpty()) {
                    sb.append(rom[0])
                    sokuon = false
                }
                sb.append(rom)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun transliterateIcuFallback(core: String): String {
        return try {
            val transliteratorClass = Class.forName("android.icu.text.Transliterator")
            val getInstance = transliteratorClass.getMethod("getInstance", String::class.java)
            val transliterator = getInstance.invoke(null, "Any-Latin; Latin-ASCII")
            val transliterate = transliteratorClass.getMethod("transliterate", String::class.java)
            (transliterate.invoke(transliterator, core) as? String) ?: core
        } catch (_: Throwable) {
            // Preserve original script safely if ICU is unavailable (e.g. local JVM unit tests)
            core
        }
    }

    private fun splitPunctuation(input: String): Triple<String, String, String> {
        var start = 0
        var end = input.length
        while (start < end && isPunctuationOrSymbol(input[start])) {
            start++
        }
        while (end > start && isPunctuationOrSymbol(input[end - 1])) {
            end--
        }
        val lead = input.substring(0, start).replace("।", "").replace("॥", "")
        val core = input.substring(start, end)
        val trail = input.substring(end).replace("।", "").replace("॥", "")
        return Triple(lead, core, trail)
    }

    private fun isPunctuationOrSymbol(ch: Char): Boolean {
        val type = Character.getType(ch).toByte()
        return type == Character.CONNECTOR_PUNCTUATION ||
            type == Character.DASH_PUNCTUATION ||
            type == Character.START_PUNCTUATION ||
            type == Character.END_PUNCTUATION ||
            type == Character.INITIAL_QUOTE_PUNCTUATION ||
            type == Character.FINAL_QUOTE_PUNCTUATION ||
            type == Character.OTHER_PUNCTUATION ||
            type == Character.MATH_SYMBOL ||
            type == Character.OTHER_SYMBOL
    }
}
