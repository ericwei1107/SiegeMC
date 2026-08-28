# Shop price fallback diagnosis — 2026-08-27

- Symptom: Siege Bow, arrows, Siege Trident, and TNT Minecart displayed as free.
- Root cause: those legacy bundles had `0` hard-coded fallback prices. Existing server configs missing their `shop.prices` entries therefore resolved them to zero when an updated jar was deployed.
- Fix: configured nonzero fallback prices for every bundle: bow 120, arrows 18, trident 240, TNT minecart 60 (and nonzero defaults for the other legacy entries).
- Regression coverage: `CurrencySettingsTest.missingPricesUseSafeFallbacksInsteadOfMakingCombatItemsFree`.
- Verification: full Maven suite passed with 199 tests; shaded jar packaged successfully.
- Status: DONE. An explicit `0` in a server config remains intentional and is still honored; remove or change it there if present.
