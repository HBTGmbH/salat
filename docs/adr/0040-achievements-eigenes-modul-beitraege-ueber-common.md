# ADR-0040 Achievements: eigenes Modul, die Module tragen ihre Achievements über `common.achievement` bei

Date: 2026-10-20
Status: Proposed

## Context and Problem Statement

Salat soll Achievements vergeben (#837, erster Teil #1501): Meilensteine wie fünf Jahre im
Unternehmen, später die erste Rechnungsanlage (`invoice`), der fünfte Kunde (`customer`) oder
pünktliches Buchen (`dailyreport`, #1502). Was erreicht ist, weiß jeweils ein anderes Modul. Gespeichert,
angekündigt (Benachrichtigung und Popup) und angezeigt (Profilseite) werden sie an einer Stelle.

Die Frage ist, wer wen kennt. Ein Modul, das die anderen fragt, bekäme mit jeder neuen Art von
Achievement eine neue Kante und stünde bald in der Mitte des Abhängigkeitsgraphen. Zudem soll nur
belohnt werden, was die Person selbst tut: keine Rangliste, keine fremden Achievements (#837).

## Considered Options

* **A — eigenes Modul `achievement`, Beiträge über `common.achievement`** (das Muster von
  `common.beta` und `common.palette`)
* **B — Ereignisse:** jedes Modul veröffentlicht ein Ereignis, wenn etwas erreicht ist, `achievement`
  hört zu
* **C — alles in `employee`:** die Profilseite gehört zur Person, die Achievements mit ihr

## Decision Outcome

Chosen: **A**. Jedes Modul deklariert seine Achievements als `AchievementDefinition` (Schlüssel,
Stufen, Icon) und liefert sie mit einem `@Component`, das `AchievementContributor` umsetzt. Dieses
beantwortet, wer bis heute welche Stufe erreicht hat und an welchem Tag. Das Modul `achievement`
sammelt die Beiträge (`AchievementRegistry`), trägt nachts ein, was neu ist, kündigt es an und
zeigt es. Es importiert nur `common`, `auth`, `employee`, `notification` und `settings`. Kein Modul
außer `settingseditor` importiert `achievement` (`ArchitectureTest`).

Der Job fragt jede Nacht alle Contributors und trägt nur ein, was noch fehlt. Der erste Lauf holt
damit die Historie nach, mit dem Tag, an dem die Stufe erreicht wurde. Ein Achievement, das ein
Modul später ergänzt, braucht weder Migration noch Nachberechnung. Ein Schlüssel, den kein Modul
mehr liefert, fällt beim Lesen still weg.

B scheidet aus, weil ein Ereignis nur Neues meldet. Die Historie müsste jedes Modul dann ein
zweites Mal selbst nachrechnen. Eine Stufe, die am Datum hängt (ein Jubiläum), hat zudem kein
auslösendes Ereignis. C scheidet aus, weil `employee` dann `invoice` und `customer` fragen müsste,
die ihrerseits `employee` importieren: ein Zyklus.

### Consequences

* Good: Ein neues Achievement ist ein Beitrag im eigenen Modul. `achievement` bleibt unverändert,
  und keine Kante kommt hinzu.
* Good: Die Historie ist mit dem ersten Lauf da, auch für ein Achievement, das erst später kommt.
* Bad: Neu erreichte Stufen erscheinen erst am Morgen danach, nicht sofort. Für Jubiläen genügt das.
  Wo es sofort sein soll (erste Rechnung), kann ein Ereignis den Job ergänzen, ohne dieses Muster
  zu ändern.
* Bad: Der Job fragt jede Nacht alles ab. Das bleibt klein, solange ein Contributor seine Antwort
  in einer Abfrage holt (Verträge aller beschäftigten Personen) statt je Person.
* Neutral: Die Texte entstehen aus dem Schlüssel (`main.achievement.<key>.group`, `.title`,
  `.level`). `AchievementTextsTest` prüft sie für jeden Beitrag in beiden Sprachen.
