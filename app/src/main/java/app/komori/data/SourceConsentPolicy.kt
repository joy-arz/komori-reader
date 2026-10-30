package app.komori.data

object SourceConsentPolicy {
    val adultSources = setOf("zap", "imhentai", "nhentai", "manhwa18", "manhwa18cc")
    const val defaultEnabled = "demonic,weeb"
    const val legacyEnabled = "demonic,weeb,mangadex,zap,imhentai,nhentai,manhwa18,manhwa18cc"

    data class Initial(val enabled: String, val confirmed: Boolean)

    fun initial(existing: String?, legacyInstall: Boolean, confirmed: Boolean?): Initial {
        val enabled = existing ?: if (legacyInstall) legacyEnabled else defaultEnabled
        return Initial(enabled, confirmed ?: enabled.split(',').any { it in adultSources })
    }

    fun needsConfirmation(source: String, enabled: Boolean, confirmed: Boolean) =
        enabled && source in adultSources && !confirmed
}
