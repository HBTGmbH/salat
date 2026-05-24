# ADR-0008 Exception-Hierarchie mit ErrorCode

Date: 2026-05-24
Status: Accepted

## Context and Problem Statement

Services müssen Fehlerzustände an Controller und UI kommunizieren: Validierungsfehler, verletzte Geschäftsregeln, fehlende Berechtigungen. Eine einheitliche Fehlerstruktur fehlt ohne explizite Entscheidung — jeder Service könnte eigene Exception-Typen oder rohe `RuntimeException`s verwenden, was die UI-Schicht zwingt, untypisierte Fehlermeldungen zu verarbeiten und i18n selbst zu koordinieren.

## Considered Options

* Rohe `RuntimeException` mit Freitext-Message
* Checked Exceptions pro Fehlerfall
* Typisierte `ErrorCodeException`-Hierarchie mit Enum-Fehlercodes und i18n-Integration

## Decision Outcome

Chosen: **Typisierte `ErrorCodeException`-Hierarchie**, weil sie Fehlerklassifizierung typsicher macht, die i18n-Auflösung zentralisiert und die UI-Schicht von Fehlertexten entkoppelt. Controller können per `catch(ErrorCodeException)` alle Business-Fehler einheitlich behandeln.

### Consequences

* Good: jeder Fehlerfall ist benannt und klassifiziert — kein `catch (Exception e)` nötig
* Good: Fehlermeldungen werden über `errorcode.<prefix>.<number>`-Keys aus den Message-Bundles aufgelöst; keine Freitexte im Code
* Good: `VetoedException` ermöglicht blockierende Vetos in Event-Listenern (→ ADR-0003) mit akkumulierten Fehlermeldungen
* Bad: jeder neue `ErrorCode` erfordert Einträge in beiden i18n-Bundles (`MessageResources.properties` + `_en`)
* Bad: neue Entwickler müssen die Hierarchie kennen, um den richtigen Subtyp zu wählen

## Exception-Hierarchie

```
RuntimeException
├── TechnicalException                — unerwartete Systemfehler
└── ErrorCodeException (abstract)     — alle Business-/Auth-Fehler tragen einen ErrorCode
    ├── AuthorizationException        — AA-* Codes
    ├── BusinessRuleException         — Domänenregelverletzungen
    ├── InvalidDataException          — Entity nicht gefunden, ungültige Eingabe
    └── VetoedException               — trägt das VetoableEvent mit Listener-Meldungen
```

## ErrorCode-Konvention

Format: Zwei-Buchstaben-Präfix + vierstellige Nummer, z. B. `CU-0001`.

Präfixe: `AA` (auth), `CO` (customer order), `CU` (customer), `EC` (employee contract), `EM` (employee), `EO` (employee order), `SO` (suborder), `TR` (time report), `RL` (release), `WD` (working day), `ETL`, `XX` (generisch).

Jeder Code hat einen zugehörigen i18n-Key: `errorcode.<prefix-lowercase>.<number>` (z. B. `errorcode.cu.0001`).

## UI-Darstellungsmuster

**Redirect-Flow** (Delete, nicht-formular-basierte POSTs):
```java
try {
    service.delete(id);
    redirectAttributes.addFlashAttribute("toastSuccess", ...);
    return "redirect:/list";
} catch (ErrorCodeException ex) {
    redirectAttributes.addFlashAttribute("toastError",
        errorCodeViewHelper.toViewMessages(ex)...);
    return "redirect:/back";
}
```

**Form-Re-render** (Create/Edit mit Validierung):
```java
try {
    service.save(entity);
} catch (ErrorCodeException ex) {
    model.addAttribute("errors", errorCodeViewHelper.toViewMessages(ex));
    // fällt durch zum Re-render
}
```

## Beteiligte Klassen

`common/exception/ErrorCodeException.java`, `ErrorCode.java`, `ServiceFeedbackMessage.java`, `ErrorCodeViewHelper.java`
