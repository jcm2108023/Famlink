# Lessons

- Robolectric Compose tests share app files between tests: reset persisted state in `@Before`.
- `performScrollToNode` needs exactly one scrollable: target `VerticalScrollAxisRange` when a screen has chip rows.
- Don't pad button labels with spaces for icon spacing; use a `Spacer` (it also breaks exact-text lookups).
- Compose `Font(..., variationSettings)` still needs `@OptIn(ExperimentalTextApi::class)`.
- Check lint `NewApi` on theme attributes against `minSdk` (e.g. `windowLightNavigationBar` is API 27).
- Never collapse an OAuth failure into "cancelled": read the status from the result intent and show the code plus what to check.
