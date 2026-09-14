# ADR-0023 Speichern ändert den Filter nicht

Date: 2026-09-14
Status: Accepted

## Context and Problem Statement

Nach dem Anlegen eines Eintrags leitet jedes Formular auf seine Liste zurück. Diese Liste ist
gefiltert — nach Suchtext, nach Kunde, nach Auftrag. Der neue Eintrag passt nicht zwangsläufig in
den Filter, und dann sieht der Benutzer eine Liste, in der das gerade Gespeicherte fehlt.

Fünf Controller haben darauf reagiert, indem sie beim Anlegen **den Filter änderten**:

```java
if (isCreate) {
    uiState.clearState(SUBORDER_FILTER);
    if (!Objects.equals(form.getCustomerId(), uiState.getLongValue(CUSTOMER_ID))) {
        uiState.clearState(CUSTOMER_ID);
    }
}
```

Das löst das Problem nicht, es verschiebt es:

- Die Liste der Unteraufträge zeigt ohne gesetzten Filter **gar nichts** (`filterSet` in
  `SuborderController.list`). Das Löschen des Filters führt also auf eine leere Liste — genau das
  Gegenteil der Absicht.
- Eine Filtereinstellung ist die Einstellung des Benutzers. Sie ohne Zutun zu verändern, verletzt
  seine Erwartung: Er hat sie gesetzt, er hat sie nicht zurückgenommen, und die nächste Liste sieht
  trotzdem anders aus als vorher.
- Das Verhalten war uneinheitlich — mal wurde der Textfilter gelöscht, mal zusätzlich die Auswahl,
  je nach Modul und je nach Vergleich mit dem Formularinhalt.

## Considered Options

* **Option A** — Status quo: Beim Anlegen einzelne Filter löschen.
* **Option B** — Den Filter so setzen, dass der neue Eintrag hineinpasst (Kunde und Auftrag des
  Formulars übernehmen, Suchtext löschen).
* **Option C** — Den Filter unverändert lassen und in der Erfolgsmeldung darauf hinweisen, dass der
  Eintrag wegen des Filters möglicherweise nicht in der Liste steht.

## Decision Outcome

Chosen: **Option C**.

Speichern ändert den Filter nicht — weder beim Anlegen noch beim Ändern. Wo ein Filter gesetzt ist,
der den Eintrag ausschließen könnte, hängt die Erfolgsmeldung einen Hinweis an:

> Der Eintrag wurde gespeichert. Der Eintrag wird in der Liste möglicherweise nicht angezeigt, weil
> ein Filter gesetzt ist.

Option B wurde verworfen: Sie ändert den Filter ebenfalls, nur geschickter, und sie trifft eine
Entscheidung über die Sicht des Benutzers, die er nicht getroffen hat. Wer nach „Wartung 2026"
gefiltert hat und einen Auftrag für einen anderen Kunden anlegt, will danach in aller Regel weiter
an „Wartung 2026" arbeiten.

### Implementierung

`FilterHintViewHelper` (`common/viewhelper/`) hängt den Hinweis an eine Meldung an, wenn einer der
übergebenen Filterschlüssel einen nicht-leeren Wert hat:

```java
redirectAttributes.addFlashAttribute("toastSuccess", filterHintViewHelper.appendTo(
    messages.getMessage("form.suborder.message.stored", "Suborder saved successfully"),
    SUBORDER_FILTER, CUSTOMER_ID, CUSTOMER_ORDER_ID));
```

Übergeben werden nur die Filter, die einen Eintrag **ausschließen** können: Suchtext und getroffene
Auswahl. Die Schalter „versteckte anzeigen" und „abgelaufene anzeigen" erweitern eine Liste nur und
können nie der Grund dafür sein, dass etwas fehlt.

Der Hinweis hängt an `main.general.message.filtered.hint` und gilt für Anlegen und Ändern
gleichermaßen: Auch eine Änderung kann einen Eintrag aus dem Filter herausfallen lassen.

### Auch ein Knopf, der ein Formular öffnet, ändert den Filter nicht

`UiStateFilter` übernimmt jeden Filterparameter, den eine Anfrage mitbringt — auch den eines
Links. Ein „Neu"-Knopf, der `?fCustomerId=…` anhängt, schreibt den Filter also beim Klick neu.

Schlimmer noch ist der Regelfall, dass der Wert **leer** ist. Der Link rendert, was die Liste beim
Aufbau der Seite gerade hatte; war das nichts, steht `?fCustomerId=` in der URL. Ein vorhandener
Parameter ist aber ein Parameter — `UiStateParameterRequestWrapper` liefert den gemerkten Wert nur
dort nach, wo die Anfrage **keinen** mitbringt. Der leere Parameter schaltet den Fallback also ab,
und das Formular öffnet ohne Vorauswahl: das Gegenteil dessen, wofür der Parameter gedacht war.

- **Der Link übergibt nichts.** Soll das neue Formular mit der aktuellen Auswahl starten, liefert
  der Fallback sie ohnehin an `createForm`; der Parameter im Link wäre nur eine Wiederholung mit
  Nebenwirkung. Die „Neu"-Knöpfe der Auftrags-, Unterauftrags- und Mitarbeiterauftragslisten zeigen
  deshalb auf den nackten Pfad.
- **Soll das Formular mit etwas anderem starten**, nennt der Link das **Formularfeld**, nicht den
  Filter: `/orders/suborders/create?customerorderId=42` nach dem Anlegen eines Auftrags,
  `/budget/pricing/create?customerorderSign=…` aus der Mitarbeitenden-Karte eines Budgetplans. Der
  Controller nimmt den expliziten Wert und fällt ohne ihn auf den gemerkten zurück.

`UiStateParameterNamingTest.noCreateLinkCarriesAFilterParameter` prüft die Regel über Templates und
Java-Quellen.

### Consequences

* Good: eine Filtereinstellung bleibt, bis der Benutzer sie ändert
* Good: das Fehlen eines Eintrags wird erklärt, statt ihn stillschweigend zu verstecken
* Good: einheitlich über alle Listen statt fünf verschiedener Sonderbehandlungen
* Bad: der Eintrag ist nach dem Speichern unter Umständen nicht sichtbar; der Benutzer muss den
  Filter selbst anpassen
* Neutral: die Listen, die ohne Filter nichts anzeigen (Aufträge, Unteraufträge,
  Mitarbeiteraufträge), zeigen nach dem Speichern ohne gesetzten Filter weiterhin nichts. Das ist
  eine eigene Frage — sie hing vorher nur deshalb an dieser, weil das Löschen des Filters genau
  dorthin führte.
