# ADR-0034 Auftrags- und Unterauftragskürzel bleiben änderbar, was sie nennt, zieht nach

Date: 2026-10-04
Status: Accepted

## Context and Problem Statement

`CustomerorderService` und `SuborderService` übernehmen beim Bearbeiten das Kürzel aus dem Formular,
und ein Unterauftrag lässt sich unter einen anderen Elternteil oder in einen anderen Auftrag
umhängen. Damit ändert sich der vollständige Auftragsschlüssel (`AUFTRAG/01/02`) des Unterauftrags
und seines ganzen Teilbaums (#1206).

Als #1206 angelegt wurde, verwiesen Budgetplan, Pauschale, Kostenzuordnung, Kundenstundensatz,
JIRA-Replikation und Berechtigungsregeln über das Kürzel. Eine Umbenennung ließ sie lautlos ins Leere
zeigen. Inzwischen verweisen sie über die id (#1204, #1205, #1212, #1322, #1323); ihre Kürzelspalten
sind Spiegel für Reports und ETL und ziehen über `CustomerorderUpdateEvent`/`SuborderUpdateEvent`
nach. Übrig bleiben:

- das Unterauftragsmuster des Kundenstundensatzes (`OrderPricing.suborderSign`), ein `LIKE` über den
  vollständigen Auftragsschlüssel — bewusst ein Muster, kein Bezug;
- Report- und ETL-Definitionen, deren SQL Kürzel als Literal enthält;
- der gemerkte Budgetfilter `fCustomerOrderSign` im UiState-Cookie.

Zu entscheiden war, ob Kürzel nach dem Anlegen änderbar bleiben.

## Considered Options

* **A — Unveränderlich:** Formular und Service verhindern die Änderung; eine Korrektur geht über
  eine Neuanlage oder einen eigenen Vorgang.
* **B — Änderbar mit Nachziehen:** Eine Umbenennung wird angekündigt, und was das Kürzel nennt,
  zieht nach oder wird der Person genannt, die umbenennt.

## Decision Outcome

Chosen: **B**. Kürzel werden korrigiert — Tippfehler, eine geänderte Nummernlogik des Kunden — und
Unteraufträge werden umgehängt, wenn ein Auftrag neu gegliedert wird. Eine Neuanlage hieße, Buchungen,
Mitarbeiteraufträge und Budgetbezüge umzuziehen; das ist teurer und fehleranfälliger als das wenige,
was nach den Umstellungen auf ids noch nachzuziehen ist.

Umgesetzt mit `SignsRenamedEvent` (in `common`, weil auch `reporting` und `etl` reagieren, die
`order` nicht importieren dürfen). Die Services veröffentlichen es nach dem Speichern mit altem und
neuem vollständigen Schlüssel. Daran hängen:

- **Budget:** `OrderReferenceService.followRename` schreibt den Anfang eines Musters um, das den
  umbenannten Auftrag oder Unterauftrag als Ganzes nennt (das alte Kürzel selbst oder das alte
  Kürzel, ein Schrägstrich und was darunter liegt). Ein Muster, das sich nicht eindeutig umschreiben
  lässt, bleibt stehen und wird genannt.
- **Reporting, ETL:** nennen die Definitionen, deren SQL das alte Kürzel als Literal enthält.
  Freies SQL wird nicht umgeschrieben.

Was nicht nachzieht, steht als Hinweis unter der Erfolgsmeldung (`NoticeViewHelper`). Der Budgetfilter
im UiState wird in einem eigenen Schritt auf die id umgestellt (#1334).

### Nachtrag (#1334)

Der Budgetfilter trägt seit #1334 die id des Auftrags (`fBudgetCustomerOrderId`, nicht
`fCustomerOrderId`, den Filter des Moduls `order`). Ein Link mit dem alten `fCustomerOrderSign` —
Lesezeichen, versandte Alarm-Mails und Benachrichtigungen — wird beim Aufruf auf die id umgeleitet,
solange das Kürzel noch einen Auftrag nennt; nach einer Umbenennung leert er den Filter. Ein im
Cookie gemerktes Kürzel verwirft der UiState-Filter, weil der Schlüssel neu ist.

### Consequences

* Good: Kürzel bleiben korrigierbar, ohne dass Bezüge lautlos verloren gehen.
* Good: Wer umbenennt, erfährt, was von Hand nachzuziehen ist, statt es später im Controlling zu
  bemerken.
* Bad: Ein Muster mit Platzhalter vor dem Ende des alten Kürzels (`AUFTRAG/0%`) und Report- oder
  ETL-SQL müssen von Hand angepasst werden.
* Neutral: Momentaufnahmen ziehen bewusst nicht nach — Rechnungen im Altbestand nennen den Auftrag,
  wie er damals hieß, ebenso die Parameter versandter Benachrichtigungen.
