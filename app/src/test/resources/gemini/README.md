The two `error-*.json` files are verbatim responses from the live Gemini API
(2026-09-28): `error-no-key.json` for a request with no key, and
`error-api-key-invalid.json` for a malformed key sent in `x-goog-api-key`.
Other Gemini responses in `GeminiBackendTest` are built from the API's discovery
document (revision 20260927), since producing them needs a real key.
