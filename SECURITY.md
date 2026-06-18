# Security Policy

OpenSefer is a client‑only reader. It has **no accounts, no backend, and stores no credentials** —
it talks only to Sefaria's public, unauthenticated API and keeps a small amount of local data
(selected books, reading position, display preferences) on the device. The attack surface is
therefore small, but we still take security reports seriously.

## Supported versions

| Version | Supported |
| ------- | --------- |
| 0.1.x   | ✅         |

OpenSefer is pre‑1.0; only the latest release line receives security fixes.

## Reporting a vulnerability

**Please do not open a public GitHub issue for security problems.**

Report it privately through GitHub's **[Private vulnerability reporting](https://docs.github.com/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)** — open the repository's **Security** tab → **Report a vulnerability**. (If it isn't enabled yet, turn on "Private vulnerability reporting" in the repository's Security settings.)

Please include:

- a description of the issue and its impact,
- the steps (or a proof of concept) to reproduce it,
- the affected platform (Android / iOS) and app version,
- any suggested remediation, if you have one.

You can expect an acknowledgement within **5 business days**. We'll work with you to confirm the
issue, agree on a fix, and coordinate disclosure. With your permission we're happy to credit you in
the release notes once a fix has shipped. Please give us a reasonable window to release a fix before
any public disclosure.

Thank you for helping keep OpenSefer and its users safe.
