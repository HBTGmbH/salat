# ADR-0015 Eigenes `error`-Modul für die Fehlerseite

Date: 2026-06-21
Status: Accepted

## Context and Problem Statement

Spring Boot zeigt bei unbehandelten Ausnahmen und HTTP-Fehlern standardmäßig eine generische
„Whitelabel Error Page". Diese Seite entspricht nicht dem Tabler-UI-Design und bietet dem Nutzer
weder eine hilfreiche Fehlermeldung noch die Möglichkeit, technische Details einfach an den
Anwendungssupport weiterzugeben.

Um die Fehlerseite zu gestalten, wird ein `ErrorController` (Spring Boot) benötigt, der gleichzeitig
auf `AuthorizedUser` (`org.tb.auth`) und `AuthorizedEmployee` (`org.tb.employee`) zugreift, um den
Sitzungskontext im Fehlerbericht darzustellen.

Das `common`-Modul darf laut ArchUnit-Regel keine anderen `org.tb.*`-Module importieren.
Das `auth`-Modul darf nur `common` und `auth` importieren — nicht `employee`.
Damit kann der Controller in keinem dieser Module platziert werden.

## Considered Options

* **Option A — Neues `error`-Modul** (`org.tb.error`)
* **Option B — Controller in `auth`-Modul**, Zugriff auf `AuthorizedEmployee` über ein Interface in `common`
* **Option C — Controller in `dailyreport`-Modul** (dem primären UI-Modul, das bereits beide Beans importiert)

## Decision Outcome

Chosen: **Option A — Neues `error`-Modul**, weil das Thema „Fehlerdarstellung" ein eigenständiges
querschnittliches Anliegen ist, das keinem fachlichen Domänenmodul gehört. Ein eigenes Modul macht
die erlaubten Abhängigkeiten explizit und hält `auth` sowie `dailyreport` frei von artfremdem Code.

Das `error`-Modul darf von `common`, `auth` und `employee` abhängen. Es darf selbst von keinem anderen
Modul importiert werden — damit entstehen keine Zyklen.

### Consequences

* Good: Klare Verantwortlichkeit; der Controller liegt dort, wo man ihn erwartet.
* Good: Keine Aufweichung der bestehenden Kopplungsregeln in `common` oder `auth`.
* Good: Das Modul kann künftig um weitere querschnittliche UI-Controller ergänzt werden (z. B. `/health`-Seite).
* Neutral: Ein weiteres Top-Level-Paket in `org.tb` — der ArchUnit-Zykluscheck überwacht es automatisch.
* Bad: Option B (Interface in `common`) hätte die Anzahl der Module konstant gehalten.
