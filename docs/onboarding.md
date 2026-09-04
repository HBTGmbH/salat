# Onboarding neuer Entwickler

Das Onboarding in diesem Projekt ist ein **Gespräch**, kein Leseauftrag. Geführt wird es vom
KI-Agenten anhand der Skill `.claude/skills/onboarding/SKILL.md` — aufrufbar mit `/onboarding`.

Dieses Dokument beschreibt den Prozess drumherum und dient als Nachschlagewerk, wenn der Dialog
nicht möglich oder nicht ausreichend ist.

## Warum dialogorientiert

Ein Onboarding-Dokument, das alles erklärt, wird entweder nicht gelesen oder veraltet. Im Gespräch
lässt sich überspringen, was jemand schon kann, an echten Dateien zeigen statt beschreiben, und
Verständnis prüfen statt voraussetzen. Der Agent hat außerdem Zugriff auf den aktuellen Stand von
Repository und Backlog — eine Datei hat den nicht.

Die Rolle dieses Dokuments ist entsprechend begrenzt: Es hält den Prozess fest und gibt eine Karte
der Konzepte mit Verweisen. Die Inhalte selbst stehen dort, wo sie gepflegt werden.

## Ablauf

| Etappe | Inhalt |
|---|---|
| 1 | Ankommen, Vorerfahrung klären, Anwendung lokal starten |
| 2 | Fachliche Domäne |
| 3 | Rollen und Rechte |
| 4 | Architektur und Module |
| 5 | Entwicklungsprozess, DoR und DoD |
| 6 | Zusammenarbeit mit der KI |
| 7 | Issues, Dokumentation, UI Style Guide |
| 8 | Aktueller Stand, Backlog, erstes Ticket |
| 9 | Abschluss, offene Fragen, nächster Schritt |

Die Etappen sind eine Reihenfolge, keine Pflichtliste. Was jemand mitbringt, wird übersprungen.
Das Onboarding darf sich über mehrere Sitzungen ziehen.

## Lead Developer

Lead Developer ist derzeit **Klaus**.

An ihn eskalieren **beide** — die neue Person und der Agent —, sobald eine Frage im Onboarding
nicht gut beantwortet werden kann:

- fachliche Fragen, die das Repository nicht beantwortet
- Entscheidungen, die noch niemand getroffen hat
- Widersprüche zwischen Dokumentation und Code
- Zugänge, Rechte, Umgebungen, Datenbestände

Solche Fragen werden gesammelt und am Ende einer Sitzung gebündelt gestellt, nicht einzeln
nachgereicht. Blockierende Fragen sofort. Wer rät statt zu fragen, produziert Wissen, das falsch
weitergegeben wird.

Widersprüche zwischen Dokumentation und Code werden als Issue erfasst.

## Die Grundkonzepte und wo sie stehen

| Konzept | Kurz | Nachschlagen |
|---|---|---|
| **Fachliche Domäne** | Zeiterfassung: Mitarbeitende buchen Zeit auf Unteraufträge von Kundenaufträgen; darauf setzen Abrechnung, Auswertung und Budgetcontrolling auf | `AGENTS.md`, Abschnitt Entity Classification |
| **Stammdaten / Bewegungsdaten** | Stammdaten werden nicht gelöscht, sondern über `hide` oder Gültigkeit ausgeblendet | [ADR-0011](adr/0011-stammdaten-vs-bewegungsdaten.md) |
| **Rollen** | `USER`, `RESTRICTED`, `BACKOFFICE`, `PEOPLE_LEAD`, `MANAGER`, `ADMIN`, abgeleitet aus `SalatUser.status` | `AGENTS.md`, Abschnitt Security Layers |
| **Rechte** | Zwei Durchsetzungsebenen: Controller und Service. Ein ausgeblendeter Menüpunkt ist keine Autorisierung | [ADR-0006](adr/0006-rollenbasierte-autorisierung.md) |
| **Architektur** | Modularer Monolith, ein Modul je fachlicher Fähigkeit, keine Zyklen | [ADR-0001](adr/0001-modular-monolith.md), `ArchitectureTest` |
| **Module** | Modultabelle und Schichtkonvention je Modul | `AGENTS.md`, Abschnitt Module Overview |
| **Cross-Modul** | Seiteneffekte über Spring Events statt direkter Aufrufe, Veto-Muster für Löschungen | [ADR-0003](adr/0003-spring-events-fuer-cross-module-kommunikation.md) |
| **Entwicklungsprozess** | Branch je Issue, PR mit `Closes #NNN`, Issue-Type per GraphQL | `AGENTS.md`, Abschnitt GitHub Workflow |
| **Definition of Ready** | `main` aktuell, dedizierter Branch | `AGENTS.md` |
| **Definition of Done** | Vollständige Checkliste: Build, Tests, Zyklen, Security, i18n, Liquibase, ADR, PR | `AGENTS.md` |
| **Zusammenarbeit mit der KI** | `AGENTS.md` ist der Vertrag für Menschen und Agenten; Verantwortung bleibt beim Mergenden | `AGENTS.md`, Abschnitt Agent usage policy; `CLAUDE.md` |
| **Issues** | GitHub, Issue-Type verpflichtend, größere Vorhaben als Epic mit Sub-Issues | Beispiele: #767, #907 |
| **Dokumentation** | `AGENTS.md` sagt *was*, ADRs sagen *warum* | [docs/adr/](adr/README.md) |
| **UI Style Guide** | Tabler auf Bootstrap 5, Listenansicht, Formularaufbau, Buttonfarben, HTMX-Muster | [docs/ui-style-guide.md](ui-style-guide.md) |
| **Lokaler Betrieb** | Start, Login über `?login-name=<sign>`, Profile, Performancemessung | `README.md` |

## Fallstricke, die regelmäßig Zeit kosten

- **`mvnw verify` enthält die E2E-Tests nicht.** Die laufen nur in der CI. Ein grüner lokaler Build
  ist kein grünes `main`.
- **Menü-Sichtbarkeit ist keine Autorisierung.** Rechte gehören in den Service, nicht ins Template.
- **i18n immer in beide Bundles**, danach alphabetisch sortieren.
- **Liquibase-Changesets werden angehängt, nie geändert.**
- **`hide` und Gültigkeitszeitraum sind getrennte Filter** und werden nicht vermischt.
- **Keine Kunden- oder Personendaten** in Code, Commits, Issues oder PRs. Die lokale Datenbank kann
  ein Produktionsabzug sein.

## Erstes Ticket

Kriterien: klein und abgeschlossen, ein Modul, ein bestehendes Muster zum Abschauen, die Definition
of Done vollständig durchlaufbar, nicht auf dem kritischen Pfad eines laufenden Epics, sichtbares
Ergebnis.

Bewährte Steigerung: erst eine reine Darstellungsänderung, dann eine Sicht mit Formular, i18n und
Tests, dann etwas, das Modulgrenzen berührt.

## Stand zum 4. September 2026

> Schnappschuss. Der Agent zieht sich den aktuellen Stand im Onboarding selbst aus Repository und
> Backlog; dieser Abschnitt ist nur der Rückfall, wenn das gerade nicht möglich ist.

**Zuletzt passiert.** Der Schwerpunkt der letzten Wochen lag auf dem Budget-Modul: Budgetpläne,
Kundenstundensätze, Mitarbeiterkostensätze, Soll-Ist-Controlling, Forecast, Dashboard mit Ampel und
Warnmeldungen. Zuletzt wurde der Zugriff darauf geregelt (#919) — Manager sehen alles,
Auftragsverantwortliche ihre nicht ausgeblendeten Aufträge, Backoffice hat kein eigenes Recht.
Daneben Verbesserungen an Matrix- und Einzelübersicht. Letztes Release: 5.0.9.

**Laufende Epics.** [#767](https://github.com/HBTGmbH/salat/issues/767) Budgetplanung und
Kostencontrolling, inzwischen in Phase 2 mit zwei offenen Punkten;
[#907](https://github.com/HBTGmbH/salat/issues/907) Phase 3, die explizite Zuordnung von
Zeitbuchungen zu Budgetplänen mit elf Sub-Issues.

**Weitere Schwerpunkte im Backlog.** Jira-Replikation, Matrix- und Einzelübersicht,
Benachrichtigungen, sowie ältere Themen zu Überstunden- und Kontenberechnung.

**Vorschläge für den Einstieg**, in dieser Reihenfolge:

1. [#838](https://github.com/HBTGmbH/salat/issues/838) — Dark Mode: Selektionsfarbe anpassen. Reine
   CSS-Änderung, sofort sichtbar, führt einmal durch den kompletten Prozess ohne fachliche Tiefe.
2. [#887](https://github.com/HBTGmbH/salat/issues/887) — Matrix-Ansicht: Summe über „Gesamt" fehlt.
   Eine Sicht, ein Modul, mit Test und i18n; das Muster steht direkt daneben.
3. [#918](https://github.com/HBTGmbH/salat/issues/918) — ArchUnit-Regeln für `budget` und `invoice`.
   Nur Testcode, zwingt aber dazu, die Modulgrenzen wirklich zu verstehen. Guter dritter Schritt.

Nicht als Einstieg geeignet: die Sub-Issues von #907 (setzen das Budget-Datenmodell voraus) und
alles zur Jira-Replikation (externes System).
