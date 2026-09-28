The two `error-*.json` files are verbatim responses from the live Gemini API
(2026-09-28): `error-no-key.json` for a request with no key, and
`error-api-key-invalid.json` for a malformed key sent in `x-goog-api-key`.
Other Gemini responses in `GeminiBackendTest` are built from the API's discovery
document (revision 20260927), since producing them needs a real key.

The `recipe-*.json` files are the Chef on Gemini (#24): a finished recipe (with
a thought part, which the app must skip), a `SAFETY` stop with no content, and a
`MAX_TOKENS` stop cut mid-JSON. They follow the same discovery document shapes,
not a live capture.
