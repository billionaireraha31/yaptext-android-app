package com.moshbari.yaptext.data

/**
 * Voice language — Android port of WhisperService.Language.
 *  - english / auto → OpenAI Whisper (via server)
 *  - bengali        → Sarvam saaras:v3 (transcribe mode)
 *  - banglish       → Sarvam saaras:v3 (translit mode)
 * The server picks the engine; the app just sends [raw].
 */
enum class Language(val raw: String, val displayName: String) {
    ENGLISH("english", "English"),
    BENGALI("bengali", "Bengali"),
    BANGLISH("banglish", "Banglish");

    companion object {
        fun fromRaw(raw: String?): Language =
            entries.firstOrNull { it.raw == raw } ?: ENGLISH
    }
}

/**
 * AI polish tone — Android port of PolishService.Tone.
 * 8 tones, matching the production yaptext.com web version. The `raw`
 * value is sent as the `tone` field to /polish; prompts live on the server.
 */
enum class Tone(val raw: String, val displayName: String, val blurb: String) {
    PROFESSIONAL("professional", "Professional", "Polished, business-appropriate."),
    CASUAL("casual", "Casual", "Relaxed, friendly, everyday chat."),
    FRIENDLY_PRO("friendly-pro", "Friendly Pro", "Warm but professional."),
    EXECUTIVE("executive", "Executive", "Direct, concise, authoritative."),
    SUPPORTIVE("supportive", "Supportive", "Empathetic, patient, helpful."),
    CREATOR("creator", "Creator", "Fun, engaging, personality-driven."),
    ACADEMIC("academic", "Academic", "Respectful, structured, clear."),
    SIMPLE("simple", "Simple", "Easy words, very clear.");
}
