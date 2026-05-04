# Project Memory

## Invoice Thymeleaf Migration (branch: 592-migrate-invoice-to-thymeleaf)
- New Spring MVC controller: `org/tb/invoice/controller/InvoiceController.java` at `/invoice`
- New form DTO: `org/tb/invoice/controller/InvoiceForm.java` (implements `InvoiceColumnHeaders`)
- New templates: `templates/invoice/show.html` (uses base layout) and `templates/invoice/print.html` (standalone)
- Print template is standalone (no base layout) to preserve exact printable invoice layout
- Session stores `invoiceData` (key `"invoiceData"`) between generate/print/export
- POST `/invoice` = updateOptions, POST `/invoice/generate` = generate, POST `/invoice/print` = print, POST `/invoice/export` = Excel
- JS `submitUpdate()` / `submitPrint()` / `submitExport()` change form action before submit
- Nav link in `layout/base.html` updated to `@{/invoice}`
- Duration formatting in templates: `${@durationUtils.format(value)}` (bean `DurationUtilsBean`)

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
