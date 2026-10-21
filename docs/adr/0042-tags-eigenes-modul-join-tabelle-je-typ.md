# ADR-0042 Tags: eigenes Modul, je getaggtem Typ eine Join-Tabelle mit Fremdschlüsseln

Date: 2026-10-21
Status: Proposed

## Context and Problem Statement

Aufträge und Unteraufträge sollen frei getaggt werden können, damit eine Auswertung sie quer über die
Hierarchie Kunde → Auftrag → Unterauftrag gemeinsam selektiert (#847). Der Mechanismus soll generisch
sein: später kommen weitere Objekte hinzu, als nächstes Mitarbeiterverträge.

Zu entscheiden war, wo die Zuordnung eines Tags zu seinem Objekt liegt. Das Issue schlug eine
polymorphe Tabelle vor, `tag_assignment (tag_id, taggable_type, taggable_id)`, weil ein weiterer Typ
dann ohne Migration auskommt. Seitdem gilt aber ADR-0036: Ein Bezug ist in der Datenbank immer eine
Spalte mit Fremdschlüssel auf der id, und auf Stammdaten eines anderen Moduls darf eine echte Referenz
zeigen.

## Considered Options

* **(a)** Polymorphe Zuordnung im Modul `tag`: `taggable_type` plus `taggable_id`, ohne Fremdschlüssel
* **(b)** Modul `tag` nur für die Tags selbst. Je getaggtem Typ legt das besitzende Modul eine
  Join-Tabelle mit Fremdschlüsseln auf sein Objekt und auf `tag` an und hält die Tags als
  `@ManyToMany` an seiner Entity
* **(c)** Tags als Spalte, etwa eine kommagetrennte Liste oder JSON, an jedem Objekt

## Decision Outcome

Chosen: **(b)**.

(a) spart je Typ ein Changeset, zahlt dafür aber an jeder anderen Stelle:

* Auf `taggable_id` kann kein Fremdschlüssel liegen, weil das Ziel je Zeile in einer anderen Tabelle
  steht. Wird ein Objekt gelöscht, muss jedes Modul seine Zuordnungen von Hand abräumen, sonst bleiben
  Waisen zurück.
* Jede Abfrage muss nach `taggable_type` filtern. Wer das vergisst, mischt stillschweigend Auftrags-
  und Vertrags-ids.
* Der Filter einer Liste wäre ein Join in eine Tabelle eines anderen Moduls, über einen
  Diskriminator-String.

(c) lässt sich weder eindeutig halten noch umbenennen, und keine Auswertung kann sauber darüber
joinen.

Mit (b) bekommt jeder Typ das, was ADR-0036 für Bezüge vorsieht:

* einen echten Fremdschlüssel mit `ON DELETE CASCADE` auf das Objekt
* eine Referenz auf Stammdaten in erlaubter Importrichtung (`order` → `tag`)
* einen Filter in den eigenen Abfragen des besitzenden Moduls

Ein weiterer Typ kostet ein Changeset mit der Join-Tabelle und ein Feld an seiner Entity. Das Modul
`tag` bleibt dabei unverändert.

### Regeln

1. `tag` importiert nur `common` und `auth` (`ArchitectureTest.tagShouldAccessCommonAuthOnly`). Es
   kennt keines der Objekte, die Tags tragen.
2. Ein `Tag` ist Stammdaten des Moduls `tag`. Name und Farbe entstehen beim Anlegen.
   - `name_key` ist der Name in Kleinbuchstaben und eindeutig. So bleiben `Festpreis` und
     `festpreis` ein Tag.
   - Die Farbe ist eine der zwölf benannten Tabler-Farben und ergibt sich fest aus dem Namen.
3. Das besitzende Modul hält die Referenz: `@ManyToMany(fetch = LAZY)`, ohne Cascade auf `Tag`, mit
   `@BatchSize` für Listen. Nach außen gibt es sie nur als unveränderliche Sicht heraus (ADR-0036,
   Regel 3).
4. `TagService` gibt keine Entities heraus (ADR-0021). `resolveTagIds(names)` liefert die ids, und
   das besitzende Modul nimmt daraus Referenzen per `EntityManager.getReference`
   (`order.persistence.TagReferences`).
5. Wie oft ein Tag verwendet wird, zählt das besitzende Modul über seine Join-Tabellen. `tag` kann das
   nicht wissen.

### Consequences

* Good: Beim Löschen bleibt keine Zuordnung zurück, ohne Event und ohne Code dafür. Das erledigen
  Hibernate und, als Absicherung, der Fremdschlüssel.
* Good: Kein Diskriminator, den eine Abfrage vergessen kann, auch nicht in Reports.
* Good: `tag` bleibt beim nächsten Typ unverändert. Der Typ bringt eine Kante ins Modul `tag`, keine
  aus ihm heraus.
* Bad: Je Typ ein Changeset, eine Join-Tabelle und ein Feld. Wer über alle Typen zugleich auswerten
  will, braucht ein `union`.
* Neutral: Umbenennen, Zusammenführen, Löschen und das Ändern der Farbe gehören in eine
  Tag-Verwaltung (Folge-Issue). Bis dahin hat `Tag` keinen Setter.
