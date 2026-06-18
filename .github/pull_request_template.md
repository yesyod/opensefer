<!--
Thanks for contributing to OpenSefer! Please keep PRs small and focused.
See CONTRIBUTING.md for the coding standards and architecture rules.
-->

## Summary

<!-- What does this change do, and why? Link any related issue (e.g. "Closes #12"). -->

## Checklist

- [ ] The build passes: `./gradlew :composeApp:assembleDebug`
- [ ] Tests pass: `./gradlew :shared:testDebugUnitTest :composeApp:testDebugUnitTest`
- [ ] Static analysis is clean: `./gradlew detekt`
- [ ] I added/updated tests for the change where it makes sense
- [ ] I updated docs / an ADR if this changes architecture or behaviour
- [ ] The change respects the layering and minimalism rules in [CONTRIBUTING.md](../CONTRIBUTING.md)
      (domain stays framework‑free; no new dependency without justification)

## Notes for reviewers

<!-- Screenshots for UI changes (Android and/or iOS), trade‑offs, anything you're unsure about. -->
