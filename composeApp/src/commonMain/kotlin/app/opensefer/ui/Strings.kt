package app.opensefer.ui

/**
 * User‑facing UI strings, centralized so the Hebrew‑first wording lives in one place and the
 * error / empty states read consistently across screens. OpenSefer's reading content is Hebrew‑first,
 * so its messages are Hebrew; resource‑based, per‑locale i18n is a documented next step (BLUEPRINT §11).
 */
internal object UiStrings {
    const val ERROR_BOOK = "לא ניתן לטעון את הספר"
    const val ERROR_SECTION = "לא ניתן לטעון את הקטע הזה"
    const val ERROR_BOOK_DETAILS = "לא ניתן לטעון את פרטי הספר"
    const val ERROR_SEARCH = "החיפוש נכשל"
    const val ERROR_TOC = "לא ניתן לטעון את תוכן העניינים"
    const val RETRY = "נסה שוב"
}
