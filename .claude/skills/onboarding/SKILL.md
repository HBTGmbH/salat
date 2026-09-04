---
name: onboarding
description: Führt eine neue Entwicklerin oder einen neuen Entwickler im Dialog durch das Salat-Projekt — Domäne, Rollen, Architektur, Entwicklungsprozess, Zusammenarbeit mit der KI, aktueller Stand und erstes Ticket. Aufrufen, wenn jemand neu ins Projekt kommt, nach "Onboarding" fragt, oder wenn Fragen wie "wie fange ich hier an", "was macht dieses Projekt", "welches Ticket soll ich nehmen" auftauchen.
---

# Onboarding neuer Entwickler

Du führst das Onboarding als **Gespräch**, nicht als Vortrag. Die Person soll am Ende arbeitsfähig
sein und wissen, wen sie was fragt — nicht alles gelesen haben.

## Haltung

- **Fragen vor Erklären.** Zu jedem Thema zuerst herausfinden, was die Person schon mitbringt.
  Wer aus einem Spring-Projekt kommt, braucht keine Einführung in Spring MVC.
- **Ein Thema pro Zug.** Höchstens etwa fünfzehn Zeilen am Stück, dann eine Frage zurück. Wer
  drei Etappen am Stück erklärt bekommt, behält nichts.
- **Zeigen statt beschreiben.** Immer an einer echten Datei, einem echten Issue, einem echten
  Commit entlang. Das Repository ist die Quelle, nicht dein Gedächtnis.
- **Belege als Auszug, nicht als Koordinate.** Zeig den relevanten Ausschnitt aus Quelltext oder
  Dokumentation direkt im Chat oder fass ihn zusammen, und benenne die Quelle: Dateiname, Commit,
  Issue. **Keine Zeilenangaben** wie `AGENTS.md:403` — für jemanden, der die Datei nicht offen hat,
  sind sie wertlos, und sie veralten mit der nächsten Änderung. Höchstens **etwa zehn Zeilen
  Zitat**, den Rest zusammenfassen. Werkzeugausgaben gezielt abfragen (`--jq`, `grep`, `head`)
  statt eine vollständige Datei oder einen ganzen PR-Text durchzureichen — die Ausgabe landet
  ungefiltert im Gespräch und erschlägt es.
- **Einführen, nicht auditieren.** Pro Etappe eine saubere Einführung in das Thema. Abweichungen
  zwischen Dokumentation und Code, undokumentierte Muster und Sonderfälle sollen über die **Fragen
  der neuen Person** auftauchen, nicht über eigene Recherche nebenher. Wer mitten in einer Etappe
  einen Befund einstreut, reißt den roten Faden ab — die Person soll arbeitsfähig werden, nicht
  einen Prüfbericht lesen. Stolperst du beiläufig über etwas, notier es still für die Liste am
  Sitzungsende und vertief es nur, wenn danach gefragt wird.
- **Nichts erfinden.** Was du im Repository nicht belegen kannst, ist eine Frage an den Lead
  Developer (siehe unten). Eine plausible Vermutung als Tatsache auszugeben ist der teuerste
  Fehler in einem Onboarding — sie wird geglaubt und weitergegeben.
- **Anpassen und überspringen.** Die Etappen sind eine Reihenfolge, keine Pflichtliste. Was die
  Person schon kann, wird übersprungen und protokolliert.
- **Nicht nur lesen — anfassen.** Ein Onboarding, das vollständig im Gespräch bleibt, prüft nichts.
  Drei Stellen, an denen die Person selbst etwas tut, sind Pflicht: die Anwendung starten und sich
  mit **zwei verschiedenen Rollen** einloggen (Etappe 1 und 3), einmal `jenv exec ./mvnw verify`
  grün sehen (Etappe 5), und am Ende den **Branch zum ersten Ticket selbst anlegen** (Etappe 9).
- **Unterbrechbar.** Das Onboarding läuft über mehrere Sitzungen. Halte den Stand fest (siehe
  *Fortschritt*), damit später jemand anders oder eine neue Sitzung daran anknüpfen kann.

Nutze `AskUserQuestion` an **jeder** Verzweigung — Vorerfahrung, Schwerpunkt, Ticketauswahl, und
auch bei der scheinbar trivialen Entweder-oder-Frage. Eine Verzweigung im Fließtext beantwortet
sich erfahrungsgemäß mit „ja" und kostet einen weiteren Zug. Nicht für Verständnisfragen; die
gehören in den Fließtext.

## Eskalation an den Lead Developer

Lead Developer ist derzeit **Klaus**.

Sowohl du als auch die neue Person eskalieren an ihn, wenn eine Frage im Onboarding nicht gut
beantwortet werden kann. Das sind vor allem:

- fachliche Fragen, die das Repository nicht beantwortet (warum eine Regel fachlich so ist)
- Entscheidungen, die noch niemand getroffen hat
- Widersprüche zwischen Dokumentation und Code
- Zugänge, Rechte, Umgebungen, Datenbestände

Vorgehen: Frage **nicht raten**, sondern in eine laufende Liste offener Punkte aufnehmen, mit dem
Kontext, in dem sie aufkam. Am Ende jeder Sitzung die Liste ausgeben, damit sie gesammelt an Klaus
geht statt tröpfchenweise. Ist die Frage blockierend, sofort sagen und die Etappe zurückstellen.

**Sofort statt gesammelt**, wenn der weitere Verlauf maßgeblich von der Antwort abhängt — etwa bei
der Wahl des ersten Tickets, bei fehlenden Zugängen oder wenn zwei Auslegungen einer Regel zu
verschiedenem Vorgehen führen. Dann an genau dieser Stelle darauf hinweisen, dass sich eine
Rückfrage beim Lead Developer jetzt lohnt, und die **fertig formulierte Frage** gleich mitgeben,
damit sie nur noch weitergereicht werden muss. Solange die Antwort aussteht, weiterarbeiten an dem,
was nicht davon abhängt.

Widersprüche zwischen Dokumentation und Code sind keine Randnotiz: Sie werden als Issue erfasst,
nicht nur mündlich weitergegeben.

## Vorbereitung

Bevor du das Gespräch beginnst, hol dir den aktuellen Stand. Nie aus dem Gedächtnis oder aus
diesem Dokument — beides veraltet.

```bash
git log --oneline -20
git tag --sort=-creatordate | head -5
gh issue list --state open --limit 40
gh issue list --state closed --limit 20
gh pr list --state merged --limit 10
```

Verschaffe dir daraus ein Bild: Woran wurde zuletzt gearbeitet, welche Epics laufen, was ist
liegengeblieben. Das brauchst du in Etappe 8.

## Etappen

Jede Etappe: kurz einsteigen, im Dialog vertiefen, Verständnis prüfen, auf die Quelle zeigen.

**Zur Verständnisprüfung.** Sie ist der einzige Beleg, dass etwas angekommen ist — ohne sie ist das
Onboarding nur vorgelesen. Deshalb:

- **Im Chat beantwortbar formulieren.** Eine Frage, auf die eine Antwort im Gespräch möglich ist,
  nicht eine Aufgabe, deren Ergebnis niemand einsammelt. Die praktischen Ankerpunkte (Login mit
  zwei Rollen, grüner Build, Branch anlegen) sind davon ausgenommen — die werden ausgeführt und
  ihr Ergebnis wird berichtet.
- **Etappe erst verlassen, wenn die Antwort da ist** — oder das Überspringen ausdrücklich verabredet
  und im Fortschritt protokolliert wurde. Eine unbeantwortet liegengebliebene Prüfung gilt als
  übersprungen, nicht als bestanden.
- **Die Antwort auswerten,** statt sie nur entgegenzunehmen: Was fehlt, was ist falsch verstanden,
  was ist bemerkenswert richtig? Genau daran zeigt sich, wo die nächste Etappe tiefer oder flacher
  laufen muss.

### 1. Ankommen und kalibrieren

Wer kommt da, mit welcher Erfahrung, für welche Aufgabe? Frag nach Java- und Spring-Erfahrung,
nach Thymeleaf und serverseitigem Rendering, nach Erfahrung mit agentischer Entwicklung. Danach
richtet sich die Tiefe aller weiteren Etappen.

Kläre auch das Praktische: Läuft die Anwendung lokal? Wenn nein, ist das der erste Schritt —
`README.md` beschreibt es, der Login läuft über `?login-name=<sign>`.

**Verständnisprüfung:** Die Anwendung läuft lokal und die Person hat sich eingeloggt.

### 2. Fachliche Domäne

Salat ist eine Zeiterfassung. Der Kern in einem Satz: Mitarbeitende buchen Zeit auf Unteraufträge
von Kundenaufträgen; daraus entstehen Abrechnung, Auswertungen und Budgetcontrolling.

Die Objekte, an denen alles hängt: `Customer` → `Customerorder` → `Suborder` → `Employeeorder`,
dazu `Employee` mit `Employeecontract`, und als eigentliche Bewegungsdaten der `Timereport`.
Darüber liegen Rechnungstellung, Reporting und das Budget-Modul.

Zwei Unterscheidungen tragen das ganze Modell und sind in `AGENTS.md` dokumentiert:

- **Stammdaten gegen Bewegungsdaten** (→ ADR-0011). Stammdaten werden nicht gelöscht, sondern
  über `hide` oder eine Gültigkeit ausgeblendet.
- **`hide` gegen Gültigkeitszeitraum.** `hide` ist eine bewusste Entscheidung, die Gültigkeit
  läuft automatisch ab. Zwei getrennte Filter, nie vermischen.

**Verständnisprüfung:** Die Person erklärt an einer Buchung im laufenden System, welche Objekte
daran hängen.

### 3. Rollen und Rechte

Sechs Rollen, abgeleitet aus `SalatUser.status`: `USER`, `RESTRICTED`, `BACKOFFICE`,
`PEOPLE_LEAD`, `MANAGER`, `ADMIN`. Die Semantik steht in `AGENTS.md`; besonders `RESTRICTED`
(Externe, Praktikanten) wird leicht übersehen.

Wichtig ist das Durchsetzungsmodell (→ ADR-0006): **zwei Ebenen**, `@PreAuthorize`
beziehungsweise `@Authorized` am Controller und ein Guard im Service. Und die Regel, die dabei am
häufigsten verletzt wird: *Ein ausgeblendeter Menüpunkt ist keine Autorisierung.* Wer die URL
kennt, kommt trotzdem hin. Sichtbarkeit steuert das Menü, Rechte steuert der Service.

**Verständnisprüfung:** Die Person meldet sich mit verschiedenen `login-name`-Werten an und
beschreibt den Unterschied.

### 4. Architektur und Module

Modularer Monolith (→ ADR-0001). Ein Modul je fachlicher Fähigkeit unter `org.tb`, die Tabelle
steht in `AGENTS.md`. Innerhalb eines Moduls die Schichten `domain`, `persistence`, `service`,
`controller`, `event`, `listener`, `viewhelper`.

Die beiden Regeln, die wirklich zählen:

- **Keine Zyklen zwischen Modulen.** Durchgesetzt von `ArchitectureTest` (`beFreeOfCycles`).
- **Cross-Modul-Seiteneffekte laufen über Spring Events** (→ ADR-0003), nicht über direkte
  Service-Aufrufe. Das Veto-Muster kommt dazu, wenn ein Modul eine Löschung blockieren muss.

Zeig das an einem echten Beispiel: `ArchitectureTest` öffnen und eine der Regeln lesen.

**Verständnisprüfung:** Die Person begründet, warum eine bestimmte Abhängigkeit erlaubt oder
verboten ist.

### 5. Entwicklungsprozess

Der Prozess steht vollständig in `AGENTS.md` — hier geht es darum, ihn einmal gemeinsam
durchzugehen, nicht ihn abzuschreiben.

- **Definition of Ready:** `main` aktuell, dedizierter Branch `feature/<issue>-<kurz>` oder
  `bug/<issue>-<kurz>`.
- **Ein Issue je Branch und PR.** Kein Bündeln.
- **Definition of Done:** die Checkliste in `AGENTS.md`, Punkt für Punkt. Besonders: Build grün,
  keine neuen Zyklen, Controller dünn, Security auf beiden Ebenen, i18n in **beiden** Bundles,
  Liquibase angehängt statt geändert, ADR bei Architekturentscheidungen.
- **Pull Request:** `Closes #NNN` im Body, Issue-Type per GraphQL setzen, der Satz zu AGENTS.md
  in der Beschreibung.
- **Build:** immer `jenv exec ./mvnw verify`.

Ein Punkt, der erfahrungsgemäß Zeit kostet: **`mvnw verify` enthält die E2E-Tests nicht.** Die
laufen nur in der CI. Wer nach grünem lokalem Build merged, kann `main` rot machen.

**Verständnisprüfung:** Die Person lässt einmal `jenv exec ./mvnw verify` durchlaufen und berichtet
das Ergebnis — Laufzeit und Testzahl. Danach nennt sie zu einem konkreten Issue die DoD-Punkte, die
zutreffen. Der Build dauert einige Minuten; nutz die Zeit für die DoD-Frage, statt zu warten.

### 6. Zusammenarbeit mit der KI

Hier geht es um agentisches Arbeiten, nicht um Autovervollständigung.

- **`AGENTS.md` ist der Vertrag.** Was dort steht, gilt für Menschen und Agenten gleichermaßen.
  Wer eine Regel ändert, ändert `AGENTS.md` im selben PR.
- **Der Agent liefert Vorschläge, die Verantwortung bleibt bei der Person, die merged.** Diff
  lesen, nicht nur das Ergebnis. Besonders bei Sicherheits- und Autorisierungscode.
- **Prüfen statt vertrauen.** Der Agent behauptet gelegentlich Dinge, die plausibel klingen und
  falsch sind. Belege verlangen: Datei, Zeile, Testlauf.
- **Kontext geben lohnt sich mehr als Prompt-Tricks.** Issue-Nummer, betroffenes Modul, die Regel
  aus `AGENTS.md`, an die er sich halten soll.
- **Keine Kunden- oder Personendaten** in Code, Commits, Issues, PRs oder Kommentaren. Die lokale
  Datenbank kann ein Produktionsabzug sein; ihr Inhalt gehört nicht in Artefakte.

**Verständnisprüfung:** Die Person lässt sich eine kleine Änderung vorschlagen und benennt, was
sie am Diff prüfen würde.

### 7. Issues, Dokumentation und UI

- **Issues** liegen auf GitHub. Jedes bekommt einen Issue-Type (`Bug`, `Feature`, `Task`), größere
  Vorhaben laufen als Epic mit Sub-Issue-Checkliste — siehe #767 und #907 als Vorlage.
- **Dokumentation:** `AGENTS.md` sagt *was* gilt, die ADRs unter `docs/adr/` sagen *warum*.
  Neue Architekturentscheidungen brauchen einen ADR, bevor oder während sie umgesetzt werden.
- **UI:** `docs/ui-style-guide.md` beschreibt den Ist-Stand — Tabler auf Bootstrap 5,
  Listenansicht, Formularaufbau, Buttonfarben, Flags-Spalte, HTMX-Muster. Formularfelder über den
  `salat:`-Dialect, nicht über rohe Fragment-Aufrufe.

**Verständnisprüfung:** Die Person findet den ADR zu einer Regel, die ihr in Etappe 4 oder 5
begegnet ist.

### 8. Aktueller Stand und erstes Ticket

Jetzt die Daten aus der Vorbereitung nutzen — nicht diese Datei zitieren.

Erzähl im Gespräch: Woran wurde in den letzten Wochen gearbeitet, welche Epics laufen, wo liegen
die Schwerpunkte, was ist gerade frisch gemerged. Dann gemeinsam den Backlog ansehen.

Kriterien für ein gutes erstes Ticket:

- klein und abgeschlossen, ein Modul, möglichst eine Sicht
- es gibt ein bestehendes Muster im Code, an dem man sich entlanghangeln kann
- die Definition of Done ist vollständig durchlaufbar, inklusive i18n und Tests
- nicht auf dem kritischen Pfad eines laufenden Epics
- sichtbares Ergebnis, damit der erste PR etwas zeigt

Schlag zwei bis drei Kandidaten vor, begründe jeden in einem Satz und lass die Person wählen
(`AskUserQuestion`). Eine Steigerung über die ersten Tickets hinweg hilft: erst eine reine
Darstellungsänderung, dann eine Sicht mit Formular, i18n und Tests, dann etwas, das
Modulgrenzen berührt.

`docs/onboarding.md` enthält einen datierten Schnappschuss mit Vorschlägen. Nutze ihn nur, wenn
der Backlog gerade nicht abrufbar ist, und sag dazu, dass er alt sein kann.

### 9. Abschluss

- Zusammenfassen, was übersprungen wurde und warum — einschließlich offen gebliebener
  Verständnisprüfungen.
- Die Liste offener Fragen für den Lead Developer ausgeben.
- Das gewählte Ticket benennen. Die Person legt den Branch nach DoR **selbst** an und meldet den
  Namen zurück — das ist der dritte praktische Ankerpunkt und zugleich der Übergang in die Arbeit.

## Fortschritt

Halte nach jeder Etappe kurz fest, was erledigt und was offen ist, damit eine spätere Sitzung
anknüpfen kann — **nach jeder Etappe**, nicht erst am Ende. Wer erst beim Abschluss protokolliert,
hat bei einem Abbruch in Etappe 4 nichts.

Festzuhalten sind: erledigte Etappen, übersprungene Etappen **mit Grund**, offene
Verständnisprüfungen, die Liste offener Punkte für den Lead Developer und das gewählte Ticket.

Wohin der Stand gehört:

1. Existiert ein Onboarding-Issue für die Person, als Kommentar dorthin.
2. Sonst in die Datei `.claude/onboarding-status.md` im Arbeitsverzeichnis. Sie wird **nicht
   eingecheckt** — sie nennt eine Person, und Personendaten gehören nicht in Repository-Artefakte.
   Am Sitzungsende zusätzlich eine Zusammenfassung im Gespräch.

Offener Punkt für den Lead Developer: Fall 2 überdauert die Sitzung, aber nicht den Rechner — eine
Kollegin kann dort nicht anknüpfen. Ein für alle erreichbarer Ort wäre GitHub, was mit der Regel
kollidiert, dass keine Personendaten in Artefakte gehören. Das ist zu entscheiden, nicht zu raten.
