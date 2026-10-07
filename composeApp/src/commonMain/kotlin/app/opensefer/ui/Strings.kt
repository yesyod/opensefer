package app.opensefer.ui

import app.opensefer.core.domain.DataError

/**
 * User‑facing UI strings, centralized so the Hebrew‑first wording lives in one place and the
 * error / empty states read consistently across screens. OpenSefer's reading content is Hebrew‑first,
 * so its chrome is Hebrew too; resource‑based, per‑locale i18n is a documented next step (BLUEPRINT §11).
 */
internal object UiStrings {
    const val APP_NAME = "OpenSefer"
    const val LIBRARY = "הספרייה שלי"
    const val CONTINUE_READING = "המשך קריאה"
    const val CONTINUE = "המשך"
    const val START_READING = "התחלת קריאה"
    const val MY_BOOKS = "ספרים"
    const val LONG_PRESS_HINT = "לחיצה ארוכה על ספר לאפשרויות"
    const val BOOK_OPTIONS = "אפשרויות הספר"
    const val ADD_BOOK = "הוספת ספר"
    const val SEARCH_BOOK = "חיפוש ספר"
    const val OPEN_BOOK = "פתיחת הספר"
    const val BOOKMARKS = "סימניות"
    const val SHOW_ALL = "הצגת הכול"
    const val SHOW_LESS = "הצגת פחות"
    const val EMPTY_LIBRARY_TITLE = "הספרייה ריקה"
    const val EMPTY_LIBRARY_BODY = "חפשו ספר ושמרו אותו כאן — הוא יחכה לכם, גם בלי אינטרנט."
    const val NEW_BOOK = "טרם נפתח"

    const val SAVE_TO_LIBRARY = "שמירה בספרייה"
    const val SAVE = "שמירה"
    const val NOT_SAVED_HINT = "הספר לא שמור בספרייה — שמרו אותו כדי לחזור למקום הזה"
    const val IN_LIBRARY = "בספרייה"
    const val SAVED_TO_LIBRARY = "נשמר בספרייה"
    const val REMOVE_FROM_LIBRARY = "הסרה מהספרייה"
    const val REMOVED_FROM_LIBRARY = "הוסר מהספרייה"
    const val UNDO = "ביטול"
    const val CONTENTS = "תוכן העניינים"
    const val ABOUT_BOOK = "פרטי הספר"
    const val DOWNLOAD_OFFLINE = "הורדה לקריאה בלי אינטרנט"
    const val AVAILABLE_OFFLINE = "שמור במכשיר — זמין גם בלי אינטרנט"
    const val DOWNLOADING = "בהורדה למכשיר…"
    const val READ_FROM_HERE = "קריאה מכאן"

    const val SEARCH = "חיפוש"
    const val SEARCH_HINT = "חפשו ספר, למשל: בראשית"
    const val SEARCH_CLEAR = "ניקוי"
    const val SEARCH_SUGGESTIONS = "ספרים מומלצים"
    const val NO_RESULTS = "לא נמצאו ספרים"

    const val BACK = "חזרה"
    const val ABOUT = "אודות"
    const val DISPLAY = "תצוגה"
    const val TEXT_SIZE = "גודל הטקסט"
    const val LANGUAGE = "שפה"
    const val THEME = "ערכת צבעים"
    const val NIKUD = "ניקוד"
    const val LANG_HEBREW = "עברית"
    const val LANG_BOTH = "שתיהן"
    const val LANG_ENGLISH = "English"
    const val THEME_SYSTEM = "אוטומטי"
    const val THEME_LIGHT = "בהיר"
    const val THEME_SEPIA = "ספיה"
    const val THEME_DARK = "כהה"

    const val COPY = "העתקה"
    const val COPIED = "הועתק"
    const val BOOKMARK = "סימנייה"
    const val ADD_BOOKMARK = "הוספת סימנייה"
    const val REMOVE_BOOKMARK = "הסרת סימנייה"
    const val BOOKMARK_ADDED = "נוספה סימנייה"
    const val BOOKMARK_REMOVED = "הסימנייה הוסרה"
    const val ALL_BOOKMARKS = "לכל הסימניות"
    const val NO_BOOKMARKS = "אין עדיין סימניות בספר הזה. להוספה, הקישו על קטע בטקסט או על סמל הסימנייה למעלה."
    const val SELECTION_HINT = "הקישו להוספת קטעים"
    const val SELECT = "בחירה להעתקה או לסימנייה"
    const val CLOSE = "סגירה"
    const val DELETE = "מחיקה"
    const val CANCEL = "ביטול"

    const val ERROR_BOOK = "לא ניתן לטעון את הספר"
    const val ERROR_SECTION = "לא ניתן לטעון את הקטע הזה"
    const val ERROR_BOOK_DETAILS = "לא ניתן לטעון את פרטי הספר"
    const val ERROR_SEARCH = "החיפוש נכשל"
    const val ERROR_TOC = "לא ניתן לטעון את תוכן העניינים"
    const val ERROR_OFFLINE = "אין חיבור לאינטרנט. ספרים ששמרתם זמינים גם בלי רשת."
    const val ERROR_NOT_FOUND = "הספר לא נמצא בספריא"
    const val ERROR_GENERIC = "משהו השתבש. נסו שוב."
    const val RETRY = "ניסיון חוזר"

    const val SAVED_TEXTS = "טקסטים שמורים במכשיר"
    const val FREE_SPACE = "פינוי מקום"
    const val FREE_SPACE_TITLE = "לפנות מקום?"
    const val FREE_SPACE_BODY = "יימחקו הטקסטים של ספרים שאינם בספרייה. הספרים שבספרייה יישארו זמינים גם בלי אינטרנט."

    fun selectedCount(count: Int): String = if (count == 1) "נבחר קטע אחד" else "נבחרו $count קטעים"
}

/** A Hebrew, user‑presentable message for any failure that reached the UI. */
internal fun Throwable.userMessage(): String = when (this) {
    is DataError.Offline -> UiStrings.ERROR_OFFLINE
    is DataError.NotFound -> UiStrings.ERROR_NOT_FOUND
    else -> UiStrings.ERROR_GENERIC
}
