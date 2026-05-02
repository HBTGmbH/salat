# Project Memory

## Message Resource Files

- Files: `src/main/resources/org/tb/web/MessageResources.properties` (DE) and `MessageResources_en.properties` (EN)
- Encoding: UTF-8
- **Never use Edit/Write tools on these files** — they corrupt the encoding
- **Always append new keys using terminal commands:**
  ```bash
  printf '\nkey=Value\n' >> MessageResources_en.properties
  printf '\nkey=Wert mit Umlaut \xc3\xbc\n' >> MessageResources.properties
  ```
- Use `\xc3\xbc` for ü, `\xc3\xb6` for ö, `\xc3\xa4` for ä etc. (UTF-8 hex bytes) in printf for German umlauts
