# ADR-0010 Deutsch als Primärsprache und ISO-8859-1-Encoding

Date: 2026-05-24
Status: Accepted

## Context and Problem Statement

Die Anwendung hat eine klar definierte Zielgruppe: deutschsprachige Mitarbeitende und Kunden. Für die i18n-Infrastruktur musste entschieden werden, welche Sprache die Standardsprache ist und welches Encoding die Properties-Dateien verwenden — beide Entscheidungen haben operative Konsequenzen für alle, die neue Texte hinzufügen.

## Considered Options

* Englisch als primäre Sprache, Deutsch als Übersetzung
* Deutsch als primäre Sprache, Englisch als Übersetzung
* UTF-8-Encoding für die Properties-Dateien
* ISO-8859-1-Encoding (Java-Properties-Standard)

## Decision Outcome

Chosen: **Deutsch als Primärsprache** (`MessageResources.properties`), weil die Zielgruppe deutschsprachig ist und eine englische Primärsprache nur Übersetzungsaufwand ohne Mehrwert erzeugen würde.

Chosen: **ISO-8859-1-Encoding**, weil Struts 1.2 dieses Encoding für seine Message-Resources voraussetzt. Solange Struts aktiv im Einsatz ist, kann nicht auf UTF-8 gewechselt werden.

Mit vollständiger Ablösung von Struts (→ ADR-0002) entfällt diese Einschränkung. Eine Migration der Bundles auf UTF-8 ist dann möglich und empfohlen.

### Consequences

* Good: deutsche Texte sind direkt lesbar, kein mentaler Übersetzungsschritt für das Entwicklungsteam
* Bad: ISO-8859-1 ist durch Struts erzwungen — Tools und Editoren müssen das Encoding explizit angeben, sonst werden Umlaute korrumpiert (Details in AGENTS.md)
* Neutral: Englische Texte in `MessageResources_en.properties` sind optional aber erwünscht; fehlende Keys fallen auf den deutschen Default zurück

## Bundle-Struktur

| Datei | Sprache | Wird ausgeliefert für |
|-------|---------|----------------------|
| `MessageResources.properties` | Deutsch (Default) | `de_DE` und alle Locales ohne spezifisches Bundle |
| `MessageResources_en.properties` | Englisch | `en` |

**Regel:** Neue Keys müssen in beide Bundles eingetragen werden. Keys sind alphabetisch sortiert.

## Beteiligte Dateien

`src/main/resources/org/tb/web/MessageResources.properties`, `MessageResources_en.properties`
