# Attribution & Text Licensing

OpenSefer is a **reader**. It ships with no Jewish texts of its own — every word of
Torah, Mishnah, Talmud, Rambam, commentary, and translation it displays is fetched on
the fly from **[Sefaria](https://www.sefaria.org)** over their open, public API.

This document explains where the texts come from, who owns them, and what attribution
is owed. It is separate from the application's own license: **OpenSefer's code is
[Apache-2.0](LICENSE); the texts are not.**

---

## Texts are from Sefaria

> **Texts provided by [Sefaria](https://www.sefaria.org).**

Sefaria is a non-profit organization building a free, open library of Jewish texts.
OpenSefer reads from their API and renders the results natively. We are deeply grateful
for the work Sefaria and its contributors have done to make these texts open.

Developer documentation: **<https://developers.sefaria.org>**.

---

## Who owns the texts, and under which license

The application code is one thing; **the texts are another.** Each text *edition*
("version") on Sefaria carries its own license set by its publisher or contributor.
Across the library you will encounter, per edition:

- **CC0** — public domain dedication (no attribution legally required, but we still
  credit the source).
- **CC-BY** — free to use **with attribution** to the named edition/translator.
- **CC-BY-SA** — free to use **with attribution**, and derivatives must share alike.

Because the license is **per edition**, two different versions of the same book can
carry different terms. The texts are © their respective publishers and contributors;
OpenSefer claims no ownership of any text it displays.

### Examples of attributed editions

The exact editions depend on what you read, but representative examples include:

- Hebrew Tanakh / Mishneh Torah base text: **"Torat Emet 363"**.
- English *Mishneh Torah*: translation by **Eliyahu Touger**, published by **Moznaim**
  (CC-BY-NC family terms per edition — always check the version's license).

When a specific edition requires attribution, that attribution is owed **to the
edition and its translator/publisher**, exactly as Sefaria reports it — not to Sefaria
generically.

---

## How to find a specific version's license

Every version's language, title, source, and license is available from Sefaria's
`versions` endpoint:

```
GET https://www.sefaria.org/api/versions/{title}
```

For example, `GET /api/versions/Genesis` lists each available version with fields such
as `versionTitle`, `language` / `languageCode`, `versionSource`, and `license`. Use
these fields to display the correct per-edition credit. OpenSefer's data layer already
reads them, and the in-app **About / Credits** screen surfaces the source and
translator for the text you're reading.

If you contribute text-facing features, please keep this per-edition attribution
intact and visible.

---

## OpenSefer is independent — not affiliated with Sefaria

> **OpenSefer is an independent project. It is not affiliated with, endorsed by, or
> sponsored by Sefaria.**

We respect Sefaria's
[name and logo policy](https://developers.sefaria.org/docs/usage-of-our-name-and-logo).
Accordingly:

- The app's name deliberately does **not** include "Sefaria".
- We use Sefaria's name only factually — to credit the source of the texts and to link
  to their site and API — never in a way that implies partnership or endorsement.
- We do not use Sefaria's logo or branding.

Please preserve this in forks and derivatives.

---

## Summary

| Item | License / source |
| --- | --- |
| OpenSefer application code | Apache-2.0 — see [LICENSE](LICENSE) |
| Jewish texts & translations | © their publishers, via Sefaria, **per-edition** CC0 / CC-BY / CC-BY-SA |
| Affiliation with Sefaria | None — independent, not endorsed |

Texts by **[Sefaria](https://www.sefaria.org)**. Thank you for keeping Torah open. 🙏
