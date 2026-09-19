# MSG-4: SMS bodies + API key leave the device; plaintext key in prefs

- **Severity:** Critical (security / privacy)
- **Status:** Open
- **Area:** AI filter / Config

## Problem
`AiNotificationFilter` POSTs sender + body with Bearer key to configurable base URL. Key lives in ordinary SharedPreferences; settings field not password-masked; custom `http://` base URL possible; `INTERNET` added to manifest.

## Evidence
`Config.aiApiKey`, `AiNotificationFilter.classify`, Settings AI dialog, `AndroidManifest.xml`.

## Acceptance
- [ ] API key stored via Keystore / encrypted prefs (not clear SharedPreferences)
- [ ] Password/masked input for key
- [ ] HTTPS-only base URL enforced
- [ ] In-app copy warns that enabling AI uploads unknown-sender SMS

## Fix direction
Keystore for secret; pin HTTPS; consider on-device-only filtering long-term.
