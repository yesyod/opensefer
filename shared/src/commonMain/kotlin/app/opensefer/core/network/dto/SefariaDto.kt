package app.opensefer.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/* ---- /api/v3/texts/{tref} ---- */

@Serializable
data class V3TextResponseDto(
    val ref: String? = null,
    val heRef: String? = null,
    val title: String? = null,
    val heTitle: String? = null,
    val versions: List<V3VersionDto> = emptyList(),
    // Sefaria reports a bad ref as HTTP 200 + {"error": "..."} — never cache such a response.
    val error: String? = null,
)

@Serializable
data class V3VersionDto(
    val versionTitle: String? = null,
    val language: String? = null,          // "he" | "en"
    val text: JsonElement? = null,         // String | List<String> | nested array
    val license: String? = null,
    val direction: String? = null,
)

/* ---- /api/index/{title} ---- */

@Serializable
data class IndexDto(
    val error: String? = null, // HTTP 200 + {"error": "..."} for an unknown title
    val title: String? = null,
    val heTitle: String? = null,
    val categories: List<String> = emptyList(),
    val heCategories: List<String> = emptyList(),
    val schema: SchemaDto? = null,
    // Rich metadata for the "About this book" screen. All optional — many books (e.g. plain Tanakh)
    // omit author/date fields. `authors` is a list of OBJECTS, and the *String fields are {en, he}.
    val authors: List<AuthorDto> = emptyList(),
    val enDesc: String? = null,
    val heDesc: String? = null,
    val enShortDesc: String? = null,
    val heShortDesc: String? = null,
    val compDateString: LocalizedTextDto? = null,
    val compPlaceString: LocalizedTextDto? = null,
    val pubDateString: LocalizedTextDto? = null,
    val pubPlaceString: LocalizedTextDto? = null,
    val era: String? = null,
)

/** An author entry on an index (`{en, he, slug}`); [slug] keys the `/api/v2/topics` author page. */
@Serializable
data class AuthorDto(
    val en: String? = null,
    val he: String? = null,
    val slug: String? = null,
)

/** A bilingual string Sefaria returns as `{"en": …, "he": …}` (descriptions, dates, places). */
@Serializable
data class LocalizedTextDto(
    val en: String? = null,
    val he: String? = null,
)

/* ---- /api/v2/topics/{slug} (author biography) ---- */

@Serializable
data class TopicDto(
    val slug: String? = null,
    val primaryTitle: LocalizedTextDto? = null,
    val description: LocalizedTextDto? = null,
    val properties: TopicPropertiesDto? = null,
)

@Serializable
data class TopicPropertiesDto(
    val birthYear: TopicPropertyDto? = null,
    val deathYear: TopicPropertyDto? = null,
    val birthPlace: TopicPropertyDto? = null,
    val deathPlace: TopicPropertyDto? = null,
    val era: TopicPropertyDto? = null,
    val enBio: TopicPropertyDto? = null,
    val heBio: TopicPropertyDto? = null,
    val enWikiLink: TopicPropertyDto? = null,
    val heWikiLink: TopicPropertyDto? = null,
)

/** Topic property values arrive wrapped as `{"value": …, "dataSource": …}`; value may be int|string. */
@Serializable
data class TopicPropertyDto(
    val value: JsonElement? = null,
) {
    /** The property's value as text (handles both numeric years and string places). */
    val text: String? get() = (value as? JsonPrimitive)?.content
}

/* ---- /api/texts/versions/{title} (editions + licenses) ---- */

@Serializable
data class VersionMetaDto(
    val versionTitle: String? = null,
    val versionTitleInHebrew: String? = null,
    val language: String? = null,          // "he" | "en"
    val license: String? = null,
    val versionSource: String? = null,
    val isPrimary: Boolean = false,
)

@Serializable
data class SchemaDto(
    val title: String? = null,
    val heTitle: String? = null,
    val key: String? = null,
    val nodeType: String? = null,
    val depth: Int? = null,
    val sectionNames: List<String> = emptyList(),
    val heSectionNames: List<String> = emptyList(),
    // lengths may contain nulls and varies; keep tolerant.
    val lengths: List<Int?> = emptyList(),
    val addressTypes: List<String> = emptyList(),
    // Complex texts (e.g. a Siddur) nest named child nodes instead of a flat depth/lengths.
    val nodes: List<SchemaDto>? = null,
    // The untitled "default" child of a complex book (its main body, e.g. Ramban on Genesis 1:1):
    // it adds nothing to the ref path.
    @SerialName("default") val isDefault: Boolean = false,
)

/* ---- /api/name/{query} ---- */

@Serializable
data class NameResponseDto(
    val completions: List<String> = emptyList(),
    @SerialName("completion_objects") val completionObjects: List<CompletionObjectDto> = emptyList(),
    @SerialName("is_book") val isBook: Boolean = false,
)

@Serializable
data class CompletionObjectDto(
    val title: String? = null,
    // `key` is a plain string for ref/topic/author hits, but a JSON *array* for TocCategory hits
    // (e.g. ["Liturgy","Siddur"]). Keep it as a raw element so one such hit can't fail the whole
    // response — otherwise searches like "סידור" throw a deserialization error.
    val key: JsonElement? = null,
    val type: String? = null,
    val he: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false,
) {
    /** The canonical ref, when [key] is a plain string; null for the list-shaped TocCategory keys. */
    val keyString: String? get() = (key as? JsonPrimitive)?.takeIf { it.isString }?.content
}
