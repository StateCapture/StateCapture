package za.co.statecapture.android.util

object AppConstants {
    val TAGLINES = listOf(
        "Capture the State of your Prepaid utilities.",
        "A more affordable commission of inquiry.",
        "Get your house in order.",
        "The official commission of inquiry into your next kilowatt.",
        "Your meter doesn't have to be under oath to tell the truth.",
        "Point of Order! Check your blocks before you buy.",
        "Your opportunity to scrutinise the tender.",
        "Keeping track of the receipts.",
        "For when you're tired of being 'shocked' by the bill.",
        "State Capture! We've been havin' it.",
        "If we had this app sooner, it would've been called Prepaid Pal!",
        "The best thing since a 5,000+ page commission of inquiry report.",
        "Ain't nobody got time for 1.7 million pages of affidavits, reports and documents.",
        "The first of a six-part instalment.",
        "Managing your utilities. No cross-examination required.",
        "Everything you need to know, without having to say 'I don't recall' under oath.",
        "Part One of a series that will definitely take four years longer than expected.",
        "Reports that are not subject to at least three extensions.",
        "Tracking your usage with more transparency than a high-level tender process.",
        "This app works even when the witness is 'out of the country for medical reasons'.",
        "Providing clarity since... well, since the last time the lights went out.",
        "Calculates exactly how much it costs to keep the 'firepool' full and the pump running.",
        "Designed to distinguish between a standard garden hose and a 'strategic firefighting asset'.",
        "No 'Special Investigating Unit' required to find out why your bill is so high.",
        "This app won't suffer from 'sudden amnesia' when the invoice arrives."
    )

    const val VAT_RATE = 0.15
    const val VAT_MULTIPLIER = 1.0 + VAT_RATE

    /**
     * Small kWh tolerance (0.05 units) used to treat a tariff block as fully exhausted
     * when cumulative monthly consumption is within floating-point precision noise of the block threshold.
     */
    const val BLOCK_EXHAUSTION_TOLERANCE_KWH = 0.05

    const val APP_DISCLAIMER =
        "This app cannot be used to purchase prepaid vouchers. Its sole purpose is ONLY to track the purchases that you have already made or help you plan for future purchases.\n\n" +
                "This app is provided free of charge 'as is'. Tariff data was gathered from public records and interpreted using AI. AI sometimes makes mistakes. Always verify tariffs with your utility provider. If you find discrepancies, please inform us using the Feedback screen."

    const val SUGGEST_PROVIDER_FORM_URL = "https://forms.gle/ryhzK5Awv2JuLVWR8"
    const val REPORT_TARIFF_FORM_URL = "https://forms.gle/YMEwrEbMeWGigkSQ9"
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=za.co.statecapture.android"

    const val ADMOB_APP_ID = "ca-app-pub-5657991828045333~7891259037"
    const val ADMOB_BANNER_UNIT_ID = "ca-app-pub-5657991828045333/5594828795" // Unit: Bottom Banner
    const val ADMOB_NATIVE_UNIT_ID = "ca-app-pub-5657991828045333/7279061111" // Unit: Support

    const val TARIFF_BASE_URL = "https://raw.githubusercontent.com/StateCapture/StateCapture/main/tariffs/"
    // SharedPreferences constants for storing index timestamps
    const val PREFS_NAME = "tariff_prefs"
    const val KEY_INDEX_LAST_UPDATED = "index_last_updated"
    const val KEY_INDEX_DOWNLOAD_TIME = "index_download_timestamp"
}
