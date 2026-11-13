package com.usman.musicplayer.model

/**
 * Deterministic, phonetically accurate Hindi (Devanagari) to Roman Hindi (English alphabet)
 * transliteration engine for USMAN MUSIC.
 *
 * Ensures that Hindi lyrics are always displayed in natural Roman Hindi (e.g.,
 * "तुम ही हो" -> "Tum hi ho", "मेरा दिल" -> "Mera dil", "तेरे बिना" -> "Tere bina",
 * "प्यार होता है" -> "Pyaar hota hai") without translating into English meanings
 * and while preserving every individual word and syllable timestamp.
 */
object HindiRomanTransliterator {

    /**
     * Returns true if [text] contains any Devanagari script character (U+0900..U+097F or U+A8E0..U+A8FF).
     * Does not match Telugu, Tamil, Malayalam, Kannada, or other non-Devanagari scripts.
     */
    fun containsDevanagari(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        for (ch in text) {
            val code = ch.code
            if (code in 0x0900..0x0963 || code in 0x0966..0x097F || code in 0xA8E0..0xA8FF) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true if [text] contains Telugu, Tamil, Malayalam, or Kannada script characters.
     * These scripts retain their original script by default unless the user explicitly selects Romanized lyrics.
     */
    fun containsNonHindiIndicScript(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        for (ch in text) {
            val code = ch.code
            if (code in 0x0B80..0x0D7F) {
                return true
            }
        }
        return false
    }

    /**
     * Curated dictionary of common Hindi / Bollywood lyric vocabulary for idiomatic Roman Hindi spelling.
     * Keys are normalized (decomposed nukta) Devanagari forms; a secondary lookup without nuktas
     * handles lyrics typed without nukta dots.
     */
    private val rawWordDictionary: Map<String, String> = mapOf(
        // Core pronouns, auxiliaries & particles
        "तुम" to "tum",
        "तू" to "tu",
        "मैं" to "main",
        "हम" to "hum",
        "आप" to "aap",
        "वो" to "woh",
        "वह" to "woh",
        "ये" to "yeh",
        "यह" to "yeh",
        "ही" to "hi",
        "भी" to "bhi",
        "तो" to "toh",
        "ना" to "na",
        "न" to "na",
        "मत" to "mat",
        "नहीं" to "nahi",
        "नाहीं" to "naahi",
        "हाँ" to "haan",
        "हां" to "haan",
        "क्या" to "kya",
        "क्यों" to "kyun",
        "क्यूँ" to "kyun",
        "क्यूं" to "kyun",
        "कैसे" to "kaise",
        "कैसा" to "kaisa",
        "कैसी" to "kaisi",
        "कहाँ" to "kahaan",
        "कहां" to "kahaan",
        "यहाँ" to "yahaan",
        "यहां" to "yahaan",
        "वहाँ" to "wahaan",
        "वहां" to "wahaan",
        "जहाँ" to "jahaan",
        "जहां" to "jahaan",
        "कब" to "kab",
        "जब" to "jab",
        "तब" to "tab",
        "अब" to "ab",
        "सब" to "sab",
        "रब" to "rab",
        "कभी" to "kabhi",
        "अभी" to "abhi",
        "सभी" to "sabhi",
        "तभी" to "tabhi",
        "यही" to "yahi",
        "वही" to "wahi",
        "कहीं" to "kahin",
        "यहीं" to "yahin",
        "वहीं" to "wahin",
        "कोई" to "koi",
        "कुछ" to "kuch",
        "सबकुछ" to "sabkuch",
        "कौन" to "kaun",
        "जो" to "jo",
        "कि" to "ki",
        "की" to "ki",
        "का" to "ka",
        "के" to "ke",
        "को" to "ko",
        "से" to "se",
        "में" to "mein",
        "पर" to "par",
        "पे" to "pe",
        "तक" to "tak",
        "बिन" to "bin",
        "बिना" to "bina",
        "बिनाह" to "bina",
        "साथ" to "saath",
        "पास" to "paas",
        "दूर" to "door",
        "औऱ" to "aur",
        "और" to "aur",
        "या" to "ya",
        "अगर" to "agar",
        "मगर" to "magar",
        "लेकिन" to "lekin",
        "फिर" to "phir",
        "सिर्फ़" to "sirf",
        "सिर्फ" to "sirf",
        "बस" to "bas",

        // Possessives & oblique pronouns
        "मेरा" to "mera",
        "मेरी" to "meri",
        "मेरे" to "mere",
        "मुझ" to "mujh",
        "मुझे" to "mujhe",
        "मुझको" to "mujhko",
        "मुझसे" to "mujhse",
        "मुझमें" to "mujhmein",
        "तेरा" to "tera",
        "तेरी" to "teri",
        "तेरे" to "tere",
        "तुझ" to "tujh",
        "तुझे" to "tujhe",
        "तुझको" to "tujhko",
        "तुझसे" to "tujhse",
        "तुझमें" to "tujhmein",
        "तुम्हारा" to "tumhaara",
        "तुम्हारी" to "tumhaari",
        "तुम्हारे" to "tumhaare",
        "तुम्हें" to "tumhein",
        "तुमको" to "tumko",
        "तुमसे" to "tumse",
        "हमारा" to "hamaara",
        "हमारी" to "hamaari",
        "हमारे" to "hamaare",
        "हमें" to "humein",
        "हमको" to "humko",
        "हमसे" to "humse",
        "उसका" to "uska",
        "उसकी" to "uski",
        "उसके" to "uske",
        "उसे" to "use",
        "उसको" to "usko",
        "उन्हें" to "unhein",
        "उनका" to "unka",
        "उनकी" to "unki",
        "उनके" to "unke",
        "उनको" to "unko",
        "इसका" to "iska",
        "इसकी" to "iski",
        "इसके" to "iske",
        "इसे" to "ise",
        "इन्हें" to "inhein",
        "जिसका" to "jiska",
        "जिसकी" to "jiski",
        "जिसके" to "jiske",
        "जिसे" to "jise",
        "जिन्हें" to "jinhein",
        "अपना" to "apna",
        "अपनी" to "apni",
        "अपने" to "apne",
        "अपनों" to "apnon",
        "खुद" to "khud",
        "ख़ुद" to "khud",

        // Common verbs & copulas
        "है" to "hai",
        "हैं" to "hain",
        "हूँ" to "hoon",
        "हूं" to "hoon",
        "हो" to "ho",
        "था" to "tha",
        "थी" to "thi",
        "थे" to "the",
        "थीं" to "thin",
        "होता" to "hota",
        "होती" to "hoti",
        "होते" to "hote",
        "होना" to "hona",
        "होके" to "hoke",
        "होकर" to "hokar",
        "हुआ" to "hua",
        "हुई" to "hui",
        "हुए" to "hue",
        "गया" to "gaya",
        "गई" to "gayi",
        "गयी" to "gayi",
        "गए" to "gaye",
        "गये" to "gaye",
        "आया" to "aaya",
        "आई" to "aayi",
        "आयी" to "aayi",
        "आए" to "aaye",
        "आये" to "aaye",
        "आ" to "aa",
        "आजा" to "aaja",
        "आओ" to "aao",
        "आना" to "aana",
        "आऊँ" to "aaun",
        "आऊंगा" to "aaunga",
        "आऊँगा" to "aaunga",
        "जा" to "jaa",
        "जाना" to "jaana",
        "जाओ" to "jaao",
        "जाऊँ" to "jaaun",
        "जाए" to "jaaye",
        "जाये" to "jaaye",
        "जाएँ" to "jaayein",
        "जायें" to "jaayein",
        "किया" to "kiya",
        "दिया" to "diya",
        "लिया" to "liya",
        "पिया" to "piya",
        "जिया" to "jiya",
        "कर" to "kar",
        "करो" to "karo",
        "करना" to "karna",
        "करता" to "karta",
        "करती" to "karti",
        "करते" to "karte",
        "करूँ" to "karoon",
        "करूं" to "karoon",
        "कह" to "keh",
        "कहो" to "kaho",
        "कहना" to "kehna",
        "कहता" to "kehta",
        "कहती" to "kehti",
        "कहते" to "kehte",
        "कहा" to "kaha",
        "कहूँ" to "kahoon",
        "रह" to "reh",
        "रहो" to "raho",
        "रहना" to "rehna",
        "रहता" to "rehta",
        "रहती" to "rehti",
        "रहते" to "rehte",
        "रहा" to "raha",
        "रही" to "rahi",
        "रहे" to "rahe",
        "रहूँ" to "rahoon",
        "सह" to "seh",
        "सहना" to "sehna",
        "सहते" to "sehte",
        "बह" to "beh",
        "बहना" to "behna",
        "बहते" to "behte",
        "सुन" to "sun",
        "सुनो" to "suno",
        "सुनना" to "sunna",
        "सुना" to "suna",
        "सुनले" to "sunle",
        "देख" to "dekh",
        "देखो" to "dekho",
        "देखना" to "dekhna",
        "देखा" to "dekha",
        "देखी" to "dekhi",
        "देखे" to "dekhe",
        "देखता" to "dekhta",
        "देखती" to "dekhti",
        "देखते" to "dekhte",
        "चल" to "chal",
        "चलो" to "chalo",
        "चलना" to "chalna",
        "चला" to "chala",
        "चली" to "chali",
        "चले" to "chale",
        "मिल" to "mil",
        "मिला" to "mila",
        "मिली" to "mili",
        "मिले" to "mile",
        "मिलना" to "milna",
        "लगा" to "laga",
        "लगी" to "lagi",
        "लगे" to "lage",
        "लगता" to "lagta",
        "लगती" to "lagti",
        "लगते" to "lagte",
        "लगाऊँ" to "lagaaun",
        "चाहूँ" to "chaahoon",
        "चाहा" to "chaaha",
        "चाहे" to "chaahe",
        "चाहता" to "chaahta",
        "चाहती" to "chaahti",
        "चाहते" to "chaahte",
        "भूल" to "bhool",
        "भूला" to "bhoola",
        "भूली" to "bhooli",
        "भूले" to "bhoole",
        "रोक" to "rok",
        "रोको" to "roko",
        "छोड़" to "chhod",
        "छोड़ा" to "chhoda",
        "तोड़" to "tod",
        "तोड़ा" to "toda",
        "जोड़" to "jod",
        "मोड़" to "mod",
        "ओढ़" to "odh",
        "लिख" to "likh",
        "लिखा" to "likha",
        "गा" to "gaa",
        "गाना" to "gaana",
        "जी" to "jee",
        "जीना" to "jeena",
        "मर" to "mar",
        "मरना" to "marna",
        "डर" to "dar",
        "डरना" to "darna",
        "भर" to "bhar",
        "भरा" to "bhara",
        "भरी" to "bhari",
        "भरे" to "bhare",
        "ठहर" to "thehar",
        "ठहरा" to "thehra",
        "ठहरी" to "thehri",
        "समझ" to "samajh",
        "समझा" to "samjha",
        "समझो" to "samjho",
        "बना" to "bana",
        "बनी" to "bani",
        "बने" to "bane",
        "बनाया" to "banaaya",
        "दिखता" to "dikhta",
        "दिखती" to "dikhti",
        "दिखते" to "dikhte",

        // Heart, love, music & poetic Bollywood vocabulary
        "प्यार" to "pyaar",
        "दिल" to "dil",
        "जान" to "jaan",
        "जानाँ" to "jaana",
        "जानां" to "jaana",
        "सनम" to "sanam",
        "कसम" to "kasam",
        "क़सम" to "kasam",
        "इश्क़" to "ishq",
        "इश्क" to "ishq",
        "मोहब्बत" to "mohabbat",
        "मुहब्बत" to "mohabbat",
        "चाहत" to "chaahat",
        "राहत" to "raahat",
        "आदत" to "aadat",
        "इबादत" to "ibaadat",
        "इजाज़त" to "ijaazat",
        "इजाजत" to "ijaazat",
        "शिकायत" to "shikaayat",
        "इनायत" to "inaayat",
        "हिफ़ाज़त" to "hifaazat",
        "क़यामत" to "qayaamat",
        "कयामत" to "kayaamat",
        "ज़िन्दगी" to "zindagi",
        "ज़िंदगी" to "zindagi",
        "जिन्दगी" to "zindagi",
        "जिंदगी" to "zindagi",
        "आशिक़ी" to "aashiqui",
        "आशिकी" to "aashiqui",
        "आशिक़" to "aashiq",
        "आशिक" to "aashiq",
        "दीवाना" to "deewana",
        "दीवानी" to "deewani",
        "दीवाने" to "deewane",
        "दीवानगी" to "deewangi",
        "परवाना" to "parwaana",
        "मस्ताना" to "mastaana",
        "अफ़साना" to "afsaana",
        "अफसाना" to "afsaana",
        "ज़माना" to "zamaana",
        "जमाना" to "zamaana",
        "ज़माने" to "zamaane",
        "फ़साना" to "fasaana",
        "तराना" to "taraana",
        "बहाना" to "bahaana",
        "ठिकाना" to "thikaana",
        "निशाना" to "nishaana",
        "ख़ज़ाना" to "khazaana",
        "खजाना" to "khazaana",
        "सुहाना" to "suhaana",
        "पुराना" to "puraana",
        "बेगाना" to "begaana",
        "अनजाना" to "anjaana",
        "अनजानी" to "anjaani",
        "अनजाने" to "anjaane",
        "यार" to "yaar",
        "यारा" to "yaara",
        "यारों" to "yaaron",
        "यारी" to "yaari",
        "दोस्त" to "dost",
        "दोस्ती" to "dosti",
        "दुश्मन" to "dushman",
        "माही" to "maahi",
        "माहिया" to "maahiya",
        "साजना" to "saajna",
        "सजना" to "sajna",
        "साजन" to "saajan",
        "बलमा" to "balma",
        "पिया" to "piya",
        "प्रीत" to "preet",
        "प्रीतम" to "preetam",
        "मीत" to "meet",
        "गीत" to "geet",
        "संगीत" to "sangeet",
        "सरगम" to "sargam",
        "धुन" to "dhun",
        "ताल" to "taal",
        "सुर" to "sur",
        "आवाज़" to "aawaaz",
        "आवाज" to "aawaaz",
        "साज़" to "saaz",
        "राज़" to "raaz",
        "नाज़" to "naaz",
        "अंदाज़" to "andaaz",
        "अंदाज" to "andaaz",
        "आग़ाज़" to "aaghaaz",
        "परवाज़" to "parwaaz",
        "अल्फ़ाज़" to "alfaaz",
        "अल्फाज" to "alfaaz",
        "जज़्बात" to "jazbaat",
        "जज्बात" to "jazbaat",
        "एहसास" to "ehsaas",
        "अहसास" to "ehsaas",
        "साँस" to "saans",
        "सांस" to "saans",
        "साँसें" to "saansein",
        "सांसें" to "saansein",
        "साँसों" to "saanson",
        "सांसों" to "saanson",
        "धड़कन" to "dhadkan",
        "धड़कनें" to "dhadkanein",
        "धड़कनों" to "dhadkanon",
        "आँख" to "aankh",
        "आंख" to "aankh",
        "आँखें" to "aankhein",
        "आंखें" to "aankhein",
        "आँखों" to "aankhon",
        "आंखों" to "aankhon",
        "आँसू" to "aansoo",
        "आंसू" to "aansoo",
        "निगाह" to "nigaah",
        "निगाहें" to "nigaahein",
        "निगाहों" to "nigaahon",
        "नज़र" to "nazar",
        "नजर" to "nazar",
        "नज़रें" to "nazrein",
        "नज़रों" to "nazron",
        "नज़ारे" to "nazaare",
        "नज़दीक" to "nazdeek",
        "चेहरा" to "chehra",
        "चेहरे" to "chehre",
        "होंठ" to "honth",
        "होंठों" to "honthon",
        "लब" to "lab",
        "लबों" to "labon",
        "ज़ुल्फ़" to "zulf",
        "जुल्फ" to "zulf",
        "ज़ुल्फ़ें" to "zulfein",
        "ज़ुल्फ़ों" to "zulfon",
        "बाहें" to "baahein",
        "बाहों" to "baahon",
        "राह" to "raah",
        "राहें" to "raahein",
        "राहों" to "raahon",
        "रास्ता" to "raasta",
        "रास्ते" to "raaste",
        "सफ़र" to "safar",
        "सफर" to "safar",
        "मंज़िल" to "manzil",
        "मंजिल" to "manzil",
        "कदम" to "kadam",
        "क़दम" to "kadam",
        "कदमों" to "kadmon",
        "ख़्वाब" to "khwaab",
        "ख्वाब" to "khwaab",
        "ख़्वाबों" to "khwaabon",
        "ख्वाबों" to "khwaabon",
        "सपना" to "sapna",
        "सपने" to "sapne",
        "सपनों" to "sapnon",
        "नींद" to "neend",
        "नींदें" to "neendein",
        "याद" to "yaad",
        "यादें" to "yaadein",
        "यादों" to "yaadon",
        "बात" to "baat",
        "बातें" to "baatein",
        "बातों" to "baaton",
        "रात" to "raat",
        "रातें" to "raatein",
        "रातों" to "raaton",
        "शाम" to "shaam",
        "सुबह" to "subah",
        "सवेरा" to "savera",
        "सवेरे" to "savere",
        "अंधेरा" to "andhera",
        "अँधेरा" to "andhera",
        "उजाला" to "ujaala",
        "रोशनी" to "roshni",
        "चाँद" to "chaand",
        "चांद" to "chaand",
        "चाँदनी" to "chaandni",
        "चांदनी" to "chaandni",
        "तारा" to "taara",
        "तारे" to "taare",
        "तारों" to "taaron",
        "सितारे" to "sitaare",
        "सितारों" to "sitaaron",
        "आसमान" to "aasmaan",
        "आसमाँ" to "aasmaan",
        "अम्बर" to "ambar",
        "अंबर" to "ambar",
        "बादल" to "baadal",
        "बारिश" to "baarish",
        "बरसात" to "barsaat",
        "सावन" to "saawan",
        "मौसम" to "mausam",
        "हवा" to "hawa",
        "हवाएँ" to "hawaayein",
        "हवाओं" to "hawaon",
        "फ़िज़ा" to "fiza",
        "फिजा" to "fiza",
        "बहार" to "bahaar",
        "फूल" to "phool",
        "ख़ुशबू" to "khushboo",
        "खुशबू" to "khushboo",
        "रंग" to "rang",
        "केसरिया" to "kesariya",
        "आग" to "aag",
        "धूप" to "dhoop",
        "छाँव" to "chhaanv",
        "छांव" to "chhaanv",
        "साया" to "saaya",
        "दरिया" to "dariya",
        "समंदर" to "samandar",
        "सागर" to "saagar",
        "किनारा" to "kinaara",
        "किनारे" to "kinaare",
        "लहर" to "lehar",
        "लहरें" to "lehrein",
        "शहर" to "shehar",
        "पहर" to "pehar",
        "ज़हर" to "zehar",
        "जहर" to "zehar",
        "क़हर" to "qehar",
        "कहर" to "kehar",
        "गली" to "gali",
        "गलियाँ" to "galiyaan",
        "गलियों" to "galiyon",
        "घर" to "ghar",
        "दर" to "dar",
        "दुनिया" to "duniya",
        "जग" to "jag",
        "जहान" to "jahaan",
        "जहाँ" to "jahaan",
        "ख़ुदा" to "khuda",
        "खुदा" to "khuda",
        "भगवान" to "bhagwaan",
        "मौला" to "maula",
        "दुआ" to "dua",
        "दुआएँ" to "duaayein",
        "सजदा" to "sajda",
        "सजदे" to "sajde",
        "वफ़ा" to "wafa",
        "वफा" to "wafa",
        "बेवफ़ा" to "bewafa",
        "बेवफा" to "bewafa",
        "जुदा" to "juda",
        "जुदाई" to "judaai",
        "तन्हा" to "tanha",
        "तन्हाई" to "tanhaai",
        "ख़ामोश" to "khaamosh",
        "खामोश" to "khaamosh",
        "ख़ामोशी" to "khaamoshi",
        "खामोशी" to "khaamoshi",
        "दर्द" to "dard",
        "ग़म" to "gham",
        "गम" to "gham",
        "ज़ख़्म" to "zakhm",
        "जख्म" to "zakhm",
        "दवा" to "dawa",
        "शिफ़ा" to "shifa",
        "सज़ा" to "saza",
        "सजा" to "saza",
        "ख़ता" to "khata",
        "खता" to "khata",
        "अदा" to "ada",
        "फ़िदा" to "fida",
        "फिदा" to "fida",
        "सदा" to "sada",
        "नशा" to "nasha",
        "मज़ा" to "maza",
        "मजा" to "maza",
        "सुकून" to "sukoon",
        "जुनून" to "junoon",
        "फ़ितूर" to "fitoor",
        "फितूर" to "fitoor",
        "ग़ुरूर" to "ghuroor",
        "गुरुर" to "ghuroor",
        "नूर" to "noor",
        "हूर" to "hoor",
        "मजबूर" to "majboor",
        "मशहूर" to "mashhoor",
        "कसूर" to "kasoor",
        "क़सूर" to "kasoor",
        "ज़्यादा" to "zyaada",
        "ज्यादा" to "zyaada",
        "थोड़ा" to "thoda",
        "थोड़ी" to "thodi",
        "थोड़े" to "thode",
        "आधा" to "aadha",
        "आधी" to "aadhi",
        "आधे" to "aadhe",
        "पूरा" to "poora",
        "पूरी" to "poori",
        "पूरे" to "poore",
        "अधूरा" to "adhoora",
        "अधूरी" to "adhoori",
        "अधूरे" to "adhoore",
        "पहला" to "pehla",
        "पहली" to "pehli",
        "पहले" to "pehle",
        "आख़िरी" to "aakhiri",
        "आखिरी" to "aakhiri",
        "नया" to "naya",
        "नयी" to "nayi",
        "नई" to "nayi",
        "नए" to "naye",
        "सच्चा" to "sachcha",
        "सच्ची" to "sachchi",
        "सच्चे" to "sachche",
        "झूठा" to "jhootha",
        "झूठी" to "jhoothi",
        "झूठे" to "jhoothe",
        "मीठा" to "meetha",
        "मीठी" to "meethi",
        "मीठे" to "meethe",
        "प्यारा" to "pyaara",
        "प्यारी" to "pyaari",
        "प्यारे" to "pyaare",
        "पागल" to "paagal",
        "काजल" to "kaajal",
        "आँचल" to "aanchal",
        "आंचल" to "aanchal",
        "पायल" to "paayal",
        "घायल" to "ghaayal",
        "शायद" to "shaayad",
        "लम्हा" to "lamha",
        "लम्हे" to "lamhe",
        "लम्हों" to "lamhon",
        "पल" to "pal",
        "कल" to "kal",
        "आज" to "aaj",
        "साल" to "saal",
        "उम्र" to "umr",
        "वक़्त" to "waqt",
        "वक्त" to "waqt",
        "किस्मत" to "kismat",
        "क़िस्मत" to "kismat",
        "तक़दीर" to "taqdeer",
        "तकदीर" to "taqdeer",
        "लकीर" to "lakeer",
        "लकीरें" to "lakeerein",
        "तस्वीर" to "tasveer",
        "ज़ंजीर" to "zanjeer",
        "हीर" to "heer",
        "रांझा" to "raanjha",
        "राँझा" to "raanjha",
        "मन्नत" to "mannat",
        "जन्नत" to "jannat",
        "हसरत" to "hasrat",
        "नफ़रत" to "nafrat",
        "नफरत" to "nafrat",
        "उल्फ़त" to "ulfat",
        "उल्फत" to "ulfat",
        "बरकत" to "barkat",
        "हरकत" to "harkat",
        "फ़ुरसत" to "fursat",
        "फुरसत" to "fursat",
        "इंतज़ार" to "intezaar",
        "इन्तज़ार" to "intezaar",
        "इंतजार" to "intezaar",
        "इज़हार" to "izhaar",
        "इकरार" to "ikraar",
        "इक़रार" to "ikraar",
        "ऐतबार" to "aitbaar",
        "दीदार" to "deedaar",
        "संसार" to "sansaar",
        "पुकार" to "pukaar",
        "ख़ुमार" to "khumaar",
        "खुमार" to "khumaar",
        "करार" to "karaar",
        "क़रार" to "karaar",
        "बेक़रार" to "bekaraar",
        "बेकरार" to "bekaraar",
        "सिफ़ारिश" to "sifaarish",
        "सिफारिश" to "sifaarish",
        "गुज़ारिश" to "guzaarish",
        "गुजारिश" to "guzaarish",
        "ख़्वाहिश" to "khwaahish",
        "ख्वाहिश" to "khwaahish",
        "साज़िश" to "saazish",
        "कोशिश" to "koshish",
        "बंदिश" to "bandish",
        "चन्ना" to "channa",
        "मेरेया" to "mereya",
        "कहानी" to "kahaani",
        "ज़ुबानी" to "zubaani",
        "जुबानी" to "zubaani",
        "निशानी" to "nishaani",
        "जवानी" to "jawaani",
        "दीवानी" to "deewaani",
        "रूह" to "rooh",
        "जिस्म" to "jism",
        "जहाँ" to "jahaan",
        "मेहरबाँ" to "meherbaan",
        "मेहरबान" to "meherbaan",
        "आशियाँ" to "aashiyaan",
        "आशियाना" to "aashiyaana",
        "दास्ताँ" to "daastaan",
        "दास्तान" to "daastaan",
        "कारवाँ" to "kaarwaan",
        "कारवां" to "kaarwaan",
        "धुआँ" to "dhuaan",
        "धुआं" to "dhuaan",
        "समाँ" to "samaan",
        "समां" to "samaan",
        "हाथ" to "haath",
        "हाथों" to "haathon",
        "उंगली" to "ungli",
        "कंधा" to "kandha",
        "कंधे" to "kandhe",
        "सीना" to "seena",
        "सीने" to "seene",
        "गले" to "gale",
        "माँ" to "maa",
        "मां" to "maa",
        "दियाँ" to "diyaan",
        "गल्लाँ" to "gallaan"
    )

    private val normalizedWordDictionary: Map<String, String> by lazy {
        val map = HashMap<String, String>(rawWordDictionary.size * 2)
        for ((k, v) in rawWordDictionary) {
            val norm = normalizeDevanagari(k)
            map[norm] = v
            val noNukta = norm.replace("\u093C", "")
            if (!map.containsKey(noNukta)) {
                map[noNukta] = v
            }
        }
        map
    }

    /**
     * Transliterates a full lyric line (and all its timed words and syllables) from Devanagari
     * into Roman Hindi while preserving every word and syllable timestamp (`startMs`, `endMs`).
     *
     * Non-Devanagari scripts (Telugu, Tamil, Malayalam, English) are returned unchanged unless
     * [preferRomanized] is true and an official `romanizedText` is available.
     */
    fun transliterateLyricLine(line: LyricLine, preferRomanized: Boolean = false): LyricLine {
        val hasDevanagariInMain = containsDevanagari(line.text) ||
            line.words.any { containsDevanagari(it.text) }
        val hasDevanagariInBg = containsDevanagari(line.backgroundVocalText) ||
            line.backgroundVocalWords.any { containsDevanagari(it.text) }

        val shouldUseOfficialRomanForNonHindi = !hasDevanagariInMain &&
            preferRomanized &&
            !line.romanizedText.isNullOrBlank() &&
            !containsDevanagari(line.romanizedText)

        if (!hasDevanagariInMain && !hasDevanagariInBg && !shouldUseOfficialRomanForNonHindi) {
            return line
        }

        var updatedText = line.text
        var updatedWords = line.words
        var updatedRomanizedText = line.romanizedText

        if (hasDevanagariInMain || shouldUseOfficialRomanForNonHindi) {
            val officialRoman = line.romanizedText?.trim()
                ?.takeIf { it.isNotEmpty() && !containsDevanagari(it) }

            if (line.words.isNotEmpty()) {
                val officialTokens = officialRoman
                    ?.split(Regex("""\s+"""))
                    ?.filter { it.isNotEmpty() }

                if (officialTokens != null && officialTokens.size == line.words.size) {
                    // Prefer official Romanized words while preserving exact word & syllable timestamps
                    val rebuilt = mapRomanWordsPreservingTimestamps(line.words, officialTokens)
                    updatedText = rebuilt.first
                    updatedWords = rebuilt.second
                    updatedRomanizedText = null
                } else if (hasDevanagariInMain) {
                    val rebuilt = transliterateTimedWords(line.words)
                    updatedText = rebuilt.first
                    updatedWords = rebuilt.second
                    if (officialRoman != null && officialRoman.equals(updatedText, ignoreCase = true)) {
                        updatedRomanizedText = null
                    } else if (hasDevanagariInMain) {
                        updatedRomanizedText = null
                    }
                } else if (officialRoman != null) {
                    updatedText = officialRoman
                    updatedRomanizedText = null
                }
            } else {
                // Line-level LRC or plain text
                updatedText = officialRoman ?: transliterateLine(line.text)
                updatedRomanizedText = null
            }
        }

        var updatedBgText = line.backgroundVocalText
        var updatedBgWords = line.backgroundVocalWords
        if (hasDevanagariInBg) {
            if (line.backgroundVocalWords.isNotEmpty()) {
                val rebuiltBg = transliterateTimedWords(line.backgroundVocalWords)
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
            romanizedText = updatedRomanizedText
        )
    }

    /**
     * Transliterates a plain or line-level Hindi string into Roman Hindi,
     * capitalizing the first letter of each sentence/line and preserving punctuation.
     */
    fun transliterateLine(line: String): String {
        if (!containsDevanagari(line)) return line

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
            if (containsDevanagari(token)) {
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
     * Transliterates a list of timed [LyricWord]s (and their [LyricSyllable]s) from Devanagari
     * to Roman Hindi while preserving every `startMs` and `endMs` timestamp and recalculating
     * character offsets (`charStart`, `charEnd`, `charStartInWord`, `charEndInWord`, `charStartInLine`, `charEndInLine`).
     */
    fun transliterateTimedWords(words: List<LyricWord>): Pair<String, List<LyricWord>> {
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
                val romWord = if (containsDevanagari(word.text)) {
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
                // Multi-syllable word: transliterate each syllable in context (word-initial / medial / word-final)
                val sylTexts = word.syllables.mapIndexed { sIdx, syl ->
                    if (containsDevanagari(syl.text)) {
                        transliterateSyllablePiece(
                            rawPiece = syl.text,
                            isWordStart = (sIdx == 0),
                            isWordEnd = (sIdx == word.syllables.lastIndex),
                            capitalizeFirst = (shouldCap && sIdx == 0)
                        )
                    } else {
                        syl.text
                    }
                }.toMutableList()

                // Check if the full combined word has a canonical dictionary entry
                val canonicalWholeWord = if (containsDevanagari(word.text)) {
                    lookupDictionaryWordPreservingPunctuation(word.text, capitalizeFirst = shouldCap)
                } else {
                    null
                }

                if (canonicalWholeWord != null && canonicalWholeWord != sylTexts.joinToString("")) {
                    alignSyllableTextsToWholeWord(sylTexts, canonicalWholeWord)
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

    private fun mapRomanWordsPreservingTimestamps(
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
                val newSyls = if (word.syllables.size == 1) {
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
                        syllables = newSyls
                    )
                )
            } else {
                val placeholderSyls = word.syllables.mapIndexed { sIdx, syl ->
                    transliterateSyllablePiece(
                        rawPiece = syl.text,
                        isWordStart = (sIdx == 0),
                        isWordEnd = (sIdx == word.syllables.lastIndex),
                        capitalizeFirst = (wIdx == 0 && sIdx == 0)
                    )
                }.toMutableList()
                alignSyllableTextsToWholeWord(placeholderSyls, romToken)

                val wordBuilder = StringBuilder()
                val newSyls = ArrayList<LyricSyllable>(word.syllables.size)
                for ((sIdx, origSyl) in word.syllables.withIndex()) {
                    val sText = placeholderSyls[sIdx]
                    val cStartInWord = wordBuilder.length
                    wordBuilder.append(sText)
                    val cEndInWord = wordBuilder.length
                    newSyls.add(
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
                        syllables = newSyls
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

    /**
     * Transliterates a single Hindi word (which may contain surrounding punctuation like `"हो,"` or `"(तुम)"`)
     * into Roman Hindi.
     */
    fun transliterateWord(rawWord: String, capitalizeFirst: Boolean = false): String {
        if (rawWord.isEmpty() || !containsDevanagari(rawWord)) return rawWord

        val dictHit = lookupDictionaryWordPreservingPunctuation(rawWord, capitalizeFirst)
        if (dictHit != null) return dictHit

        return transliterateSyllablePiece(
            rawPiece = rawWord,
            isWordStart = true,
            isWordEnd = true,
            capitalizeFirst = capitalizeFirst
        )
    }

    private fun lookupDictionaryWordPreservingPunctuation(
        rawWord: String,
        capitalizeFirst: Boolean
    ): String? {
        val (leadPunct, core, trailPunct) = splitPunctuation(rawWord)
        if (core.isEmpty()) return null
        val normalizedCore = normalizeDevanagari(core)
        val dictMatch = normalizedWordDictionary[normalizedCore]
            ?: normalizedWordDictionary[normalizedCore.replace("\u093C", "")]
            ?: return null

        val cased = if (capitalizeFirst && dictMatch.isNotEmpty()) {
            dictMatch.replaceFirstChar { it.uppercaseChar() }
        } else {
            dictMatch
        }
        return "$leadPunct$cased$trailPunct"
    }

    private fun transliterateSyllablePiece(
        rawPiece: String,
        isWordStart: Boolean,
        isWordEnd: Boolean,
        capitalizeFirst: Boolean
    ): String {
        val (leadPunct, core, trailPunct) = splitPunctuation(rawPiece)
        if (core.isEmpty()) return "$leadPunct$trailPunct"

        if (isWordStart && isWordEnd) {
            val normalizedCore = normalizeDevanagari(core)
            val dictMatch = normalizedWordDictionary[normalizedCore]
                ?: normalizedWordDictionary[normalizedCore.replace("\u093C", "")]
            if (dictMatch != null) {
                val cased = if (capitalizeFirst && dictMatch.isNotEmpty()) {
                    dictMatch.replaceFirstChar { it.uppercaseChar() }
                } else {
                    dictMatch
                }
                return "$leadPunct$cased$trailPunct"
            }
        }

        val transliteratedCore = transliterateCoreDevanagari(
            normalized = normalizeDevanagari(core),
            isWordStart = isWordStart,
            isWordEnd = isWordEnd
        )

        val cased = if (capitalizeFirst && transliteratedCore.isNotEmpty()) {
            transliteratedCore.replaceFirstChar { it.uppercaseChar() }
        } else {
            transliteratedCore
        }
        return "$leadPunct$cased$trailPunct"
    }

    private fun splitPunctuation(input: String): Triple<String, String, String> {
        var start = 0
        var end = input.length
        while (start < end && !isDevanagariCoreChar(input[start])) {
            start++
        }
        while (end > start && !isDevanagariCoreChar(input[end - 1])) {
            end--
        }
        val lead = input.substring(0, start).replace("।", "").replace("॥", "")
        val core = input.substring(start, end)
        val trail = input.substring(end).replace("।", "").replace("॥", "")
        return Triple(lead, core, trail)
    }

    private fun isDevanagariCoreChar(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x0900..0x0963) || (code in 0x0966..0x097F) || ch == '\u200C' || ch == '\u200D'
    }

    private fun normalizeDevanagari(input: String): String {
        val sb = StringBuilder(input.length + 4)
        for (ch in input) {
            when (ch) {
                '\u0958' -> sb.append("\u0915\u093C") // क़
                '\u0959' -> sb.append("\u0916\u093C") // ख़
                '\u095A' -> sb.append("\u0917\u093C") // ग़
                '\u095B' -> sb.append("\u091C\u093C") // ज़
                '\u095C' -> sb.append("\u0921\u093C") // ड़
                '\u095D' -> sb.append("\u0922\u093C") // ढ़
                '\u095E' -> sb.append("\u092B\u093C") // फ़
                '\u095F' -> sb.append("\u092F\u093C") // य़
                '\u200C', '\u200D' -> {} // Strip ZWNJ / ZWJ
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private data class PhoneticUnit(
        val isConsonant: Boolean,
        val baseRoman: String,
        val rawConsonant: Char = '\u0000',
        val hasNukta: Boolean = false,
        val hasHalant: Boolean = false,
        val matra: Char? = null,
        val hasAnusvara: Boolean = false,
        val hasChandrabindu: Boolean = false,
        val hasVisarga: Boolean = false
    ) {
        val hasExplicitVowel: Boolean
            get() = !isConsonant || matra != null

        val hasAnyVowelSound: Boolean
            get() = !isConsonant || !hasHalant
    }

    private fun transliterateCoreDevanagari(
        normalized: String,
        isWordStart: Boolean,
        isWordEnd: Boolean
    ): String {
        val units = parsePhoneticUnits(normalized)
        if (units.isEmpty()) return ""

        val out = StringBuilder()

        for ((idx, unit) in units.withIndex()) {
            val prevUnit = units.getOrNull(idx - 1)
            val nextUnit = units.getOrNull(idx + 1)
            val isAtVeryEnd = isWordEnd && (idx == units.lastIndex)

            if (!unit.isConsonant) {
                // Independent vowel or digit
                val vStr = when (unit.rawConsonant) {
                    'अ' -> "a"
                    'आ' -> if (isAtVeryEnd && prevUnit != null && !unit.hasAnusvara && !unit.hasChandrabindu) "a" else "aa"
                    'इ' -> "i"
                    'ई' -> if (isAtVeryEnd && prevUnit != null) "i" else "ee"
                    'उ' -> "u"
                    'ऊ' -> if (prevUnit != null && (unit.hasAnusvara || unit.hasChandrabindu)) "un" else "oo"
                    'ऋ', 'ॠ' -> "ri"
                    'ए', 'ऍ', 'ऎ' -> "e"
                    'ऐ' -> "ai"
                    'ओ', 'ऑ', 'ॊ' -> "o"
                    'औ' -> "au"
                    else -> unit.baseRoman
                }
                out.append(vStr)
                if (unit.rawConsonant != 'ऊ' || prevUnit == null || (!unit.hasAnusvara && !unit.hasChandrabindu)) {
                    appendNasalOrVisarga(out, unit, nextUnit)
                }
                continue
            }

            // Consonant base
            val consonantRoman = if (unit.rawConsonant == 'व' && !unit.hasNukta && prevUnit?.hasHalant == true) {
                "w"
            } else {
                unit.baseRoman
            }
            out.append(consonantRoman)

            when {
                unit.hasHalant -> {
                    // Conjunct consonant without vowel
                }
                unit.matra != null -> {
                    // Explicit dependent vowel matra
                    when (unit.matra) {
                        '\u093E' -> { // ा
                            val useDoubleA = when {
                                !isAtVeryEnd -> true
                                unit.hasAnusvara || unit.hasChandrabindu -> true
                                units.size == 1 && isWordStart && unit.rawConsonant != 'न' && unit.rawConsonant != 'थ' -> true
                                else -> false
                            }
                            out.append(if (useDoubleA) "aa" else "a")
                        }
                        '\u093F' -> out.append("i") // ि
                        '\u0940' -> { // ी
                            out.append(if (isAtVeryEnd) "i" else "ee")
                        }
                        '\u0941' -> out.append("u") // ु
                        '\u0942' -> { // ू
                            val useSingleU = isAtVeryEnd &&
                                !unit.hasAnusvara &&
                                !unit.hasChandrabindu &&
                                units.size == 1 &&
                                isWordStart
                            out.append(if (useSingleU) "u" else "oo")
                        }
                        '\u0943', '\u0944' -> out.append("ri") // ृ
                        '\u0947', '\u0945', '\u0946' -> { // े
                            if (isAtVeryEnd && (unit.hasAnusvara || unit.hasChandrabindu)) {
                                out.append("ei")
                            } else {
                                out.append("e")
                            }
                        }
                        '\u0948' -> out.append("ai") // ै
                        '\u094B', '\u0949', '\u094A' -> out.append("o") // ो
                        '\u094C' -> out.append("au") // ौ
                    }
                }
                else -> {
                    // Inherent schwa ('a') — apply Hindi schwa deletion rules
                    val keepSchwa = shouldKeepInherentSchwa(
                        units = units,
                        idx = idx,
                        isWordStart = isWordStart,
                        isWordEnd = isWordEnd
                    )
                    if (keepSchwa) {
                        out.append("a")
                    }
                }
            }

            appendNasalOrVisarga(out, unit, nextUnit)
        }

        return out.toString()
    }

    private fun shouldKeepInherentSchwa(
        units: List<PhoneticUnit>,
        idx: Int,
        isWordStart: Boolean,
        isWordEnd: Boolean
    ): Boolean {
        val unit = units[idx]
        val prevUnit = units.getOrNull(idx - 1)
        val nextUnit = units.getOrNull(idx + 1)
        val isAtVeryEnd = isWordEnd && (idx == units.lastIndex)

        // 1. If the consonant carries a nasal (ं / ँ) or visarga (ः), its inherent 'a' is always pronounced (e.g., रंग -> rang, संग -> sang)
        if (unit.hasAnusvara || unit.hasChandrabindu || unit.hasVisarga) {
            return true
        }

        // 2. If followed by an independent vowel (e.g., दुआ, हुआ), keep 'a'
        if (nextUnit != null && !nextUnit.isConsonant) {
            return true
        }

        // 3. If followed by a conjunct cluster (nextUnit hasHalant), keep 'a' so the cluster is pronounceable (e.g., पसन्द -> pasand)
        if (nextUnit != null && nextUnit.hasHalant) {
            return true
        }

        // 4. Word-final position: delete schwa in Hindi unless single-consonant word or final conjunct with y/r/v
        if (isAtVeryEnd) {
            if (units.size == 1 && isWordStart) {
                return true
            }
            if (prevUnit?.hasHalant == true && (unit.rawConsonant == 'य' || unit.rawConsonant == 'र' || unit.rawConsonant == 'व')) {
                return true
            }
            return false
        }

        // 5. Word-initial syllable (or second consonant of a word-initial conjunct like प्यार, प्रीत, क्या): always keep 'a'
        if (idx == 0 && isWordStart) {
            return true
        }
        if (idx == 1 && isWordStart && prevUnit?.hasHalant == true) {
            return true
        }

        // 6. If preceded by a halant (conjunct completion in middle of word), keep 'a'
        if (prevUnit?.hasHalant == true) {
            return true
        }

        // 7. Medial schwa deletion (Ohala's rule):
        // Delete inherent 'a' if preceded by a vowel-bearing unit and followed by a consonant with an explicit matra
        // (e.g., सुनना -> sunna, करता -> karta, कितना -> kitna, अपना -> apna, सपना -> sapna, चाँदनी -> chaandni, रोशनी -> roshni)
        if (prevUnit != null && prevUnit.hasAnyVowelSound && nextUnit != null && nextUnit.isConsonant && nextUnit.matra != null) {
            return false
        }

        // 8. 4-syllable C1a-C2a-C3a-C4 pattern (e.g., धड़कन -> dhadkan, सरगम -> sargam, मतलब -> matlab, नफ़रत -> nafrat):
        // Delete schwa on C2 (idx == 1) when C3 has inherent 'a' and C4 is word-final
        if (idx == 1 && units.size >= 4 && nextUnit != null && nextUnit.isConsonant && !nextUnit.hasHalant && nextUnit.matra == null) {
            val unitAfterNext = units.getOrNull(idx + 2)
            if (unitAfterNext != null && unitAfterNext.isConsonant) {
                return false
            }
        }

        return true
    }

    private fun appendNasalOrVisarga(
        out: StringBuilder,
        unit: PhoneticUnit,
        nextUnit: PhoneticUnit?
    ) {
        if (unit.hasAnusvara) {
            val nextChar = nextUnit?.rawConsonant
            val isLabial = nextChar == 'प' || nextChar == 'फ' || nextChar == 'ब' || nextChar == 'भ' || nextChar == 'म'
            out.append(if (isLabial) "m" else "n")
        } else if (unit.hasChandrabindu) {
            out.append("n")
        }
        if (unit.hasVisarga) {
            out.append("h")
        }
    }

    private fun parsePhoneticUnits(normalized: String): List<PhoneticUnit> {
        val units = mutableListOf<PhoneticUnit>()
        var i = 0
        val len = normalized.length

        while (i < len) {
            val ch = normalized[i]

            // 1. Check special 3-char conjuncts: क्ष (ksh), ज्ञ (gy), त्र (tr), श्र (shr)
            if (i + 2 < len && normalized[i + 1] == '\u094D') {
                val c2 = normalized[i + 2]
                val specialRoman = when {
                    ch == 'क' && c2 == 'ष' -> "ksh"
                    ch == 'ज' && c2 == 'ञ' -> "gy"
                    ch == 'त' && c2 == 'र' -> "tr"
                    ch == 'श' && c2 == 'र' -> "shr"
                    else -> null
                }
                if (specialRoman != null) {
                    i += 3
                    var hasHalant = false
                    var matra: Char? = null
                    var hasAnusvara = false
                    var hasChandrabindu = false
                    var hasVisarga = false

                    while (i < len) {
                        val m = normalized[i]
                        when {
                            m == '\u094D' -> { hasHalant = true; i++ }
                            isVowelMatra(m) -> { matra = m; i++ }
                            m == '\u0902' -> { hasAnusvara = true; i++ }
                            m == '\u0901' -> { hasChandrabindu = true; i++ }
                            m == '\u0903' -> { hasVisarga = true; i++ }
                            else -> break
                        }
                    }
                    units.add(
                        PhoneticUnit(
                            isConsonant = true,
                            baseRoman = specialRoman,
                            rawConsonant = c2,
                            hasHalant = hasHalant,
                            matra = matra,
                            hasAnusvara = hasAnusvara,
                            hasChandrabindu = hasChandrabindu,
                            hasVisarga = hasVisarga
                        )
                    )
                    continue
                }
            }

            // 2. Check standard consonant (U+0915..U+0939)
            if (ch.code in 0x0915..0x0939) {
                i++
                var hasNukta = false
                if (i < len && normalized[i] == '\u093C') {
                    hasNukta = true
                    i++
                }
                var hasHalant = false
                var matra: Char? = null
                var hasAnusvara = false
                var hasChandrabindu = false
                var hasVisarga = false

                while (i < len) {
                    val m = normalized[i]
                    when {
                        m == '\u094D' -> { hasHalant = true; i++ }
                        isVowelMatra(m) -> { matra = m; i++ }
                        m == '\u0902' -> { hasAnusvara = true; i++ }
                        m == '\u0901' -> { hasChandrabindu = true; i++ }
                        m == '\u0903' -> { hasVisarga = true; i++ }
                        else -> break
                    }
                }

                units.add(
                    PhoneticUnit(
                        isConsonant = true,
                        baseRoman = mapConsonantRoman(ch, hasNukta),
                        rawConsonant = ch,
                        hasNukta = hasNukta,
                        hasHalant = hasHalant,
                        matra = matra,
                        hasAnusvara = hasAnusvara,
                        hasChandrabindu = hasChandrabindu,
                        hasVisarga = hasVisarga
                    )
                )
                continue
            }

            // 3. Check independent vowels (U+0904..U+0914, U+0960..U+0961)
            if (ch.code in 0x0904..0x0914 || ch.code in 0x0960..0x0961 || ch.code in 0x0972..0x0977) {
                i++
                var hasAnusvara = false
                var hasChandrabindu = false
                var hasVisarga = false
                while (i < len) {
                    val m = normalized[i]
                    when (m) {
                        '\u0902' -> { hasAnusvara = true; i++ }
                        '\u0901' -> { hasChandrabindu = true; i++ }
                        '\u0903' -> { hasVisarga = true; i++ }
                        else -> break
                    }
                }
                units.add(
                    PhoneticUnit(
                        isConsonant = false,
                        baseRoman = mapIndependentVowelRoman(ch),
                        rawConsonant = ch,
                        hasAnusvara = hasAnusvara,
                        hasChandrabindu = hasChandrabindu,
                        hasVisarga = hasVisarga
                    )
                )
                continue
            }

            // 4. Devanagari digits (U+0966..U+096F)
            if (ch.code in 0x0966..0x096F) {
                val digit = (ch.code - 0x0966).toString()
                units.add(PhoneticUnit(isConsonant = false, baseRoman = digit, rawConsonant = ch))
                i++
                continue
            }

            // 5. Standalone matra (e.g. when a syllable span splits a matra onto a separate span)
            if (isVowelMatra(ch)) {
                val matraRoman = when (ch) {
                    '\u093E' -> "a"
                    '\u093F' -> "i"
                    '\u0940' -> "i"
                    '\u0941' -> "u"
                    '\u0942' -> "oo"
                    '\u0943', '\u0944' -> "ri"
                    '\u0947', '\u0945', '\u0946' -> "e"
                    '\u0948' -> "ai"
                    '\u094B', '\u0949', '\u094A' -> "o"
                    '\u094C' -> "au"
                    else -> ""
                }
                i++
                var hasAnusvara = false
                var hasChandrabindu = false
                while (i < len && (normalized[i] == '\u0902' || normalized[i] == '\u0901')) {
                    if (normalized[i] == '\u0902') hasAnusvara = true
                    if (normalized[i] == '\u0901') hasChandrabindu = true
                    i++
                }
                units.add(
                    PhoneticUnit(
                        isConsonant = false,
                        baseRoman = matraRoman,
                        rawConsonant = ch,
                        hasAnusvara = hasAnusvara,
                        hasChandrabindu = hasChandrabindu
                    )
                )
                continue
            }

            i++
        }

        return units
    }

    private fun isVowelMatra(ch: Char): Boolean {
        val code = ch.code
        return code in 0x093E..0x094C || code in 0x094E..0x094F || code in 0x0955..0x0957 || code in 0x0962..0x0963
    }

    private fun mapIndependentVowelRoman(ch: Char): String = when (ch) {
        'अ' -> "a"
        'आ' -> "aa"
        'इ' -> "i"
        'ई' -> "ee"
        'उ' -> "u"
        'ऊ' -> "oo"
        'ऋ', 'ॠ' -> "ri"
        'ए', 'ऍ', 'ऎ' -> "e"
        'ऐ' -> "ai"
        'ओ', 'ऑ', 'ॊ' -> "o"
        'औ' -> "au"
        else -> "a"
    }

    private fun mapConsonantRoman(ch: Char, hasNukta: Boolean): String = when (ch) {
        'क' -> if (hasNukta) "q" else "k"
        'ख' -> "kh"
        'ग' -> if (hasNukta) "gh" else "g"
        'घ' -> "gh"
        'ङ' -> "n"
        'च' -> "ch"
        'छ' -> "chh"
        'ज' -> if (hasNukta) "z" else "j"
        'झ' -> "jh"
        'ञ' -> "n"
        'ट' -> "t"
        'ठ' -> "th"
        'ड' -> "d"
        'ढ' -> "dh"
        'ण' -> "n"
        'त' -> "t"
        'थ' -> "th"
        'द' -> "d"
        'ध' -> "dh"
        'न', 'ऩ' -> "n"
        'प' -> "p"
        'फ' -> if (hasNukta) "f" else "ph"
        'ब' -> "b"
        'भ' -> "bh"
        'म' -> "m"
        'य' -> "y"
        'र', 'ऱ' -> "r"
        'ल', 'ळ', 'ऴ' -> "l"
        'व' -> "v"
        'श', 'ष' -> "sh"
        'स' -> "s"
        'ह' -> "h"
        else -> ""
    }
}
