# ADR-0012 Archivierung von Stammdaten: `hide`-Flag und Gültigkeitsspannen

Date: 2026-06-04
Status: Accepted

## Context and Problem Statement

Stammdaten (Kunden, Aufträge, Unteraufträge, Mitarbeiter, Verträge …) werden von Bewegungsdaten
referenziert und dürfen daher nicht gelöscht werden, auch wenn sie im laufenden Betrieb nicht mehr
aktiv genutzt werden. Gleichzeitig sollen veraltete oder nicht mehr aktive Objekte in der Benutzeroberfläche
nicht als wählbare Optionen auftauchen und die Dropdowns unnötig überfrachten.

Zwei unabhängige Mechanismen steuern, ob ein Stammdatum in Dropdowns erscheint:

1. **`hide`-Flag** — manuelle Entscheidung: das Objekt ist veraltet und soll für neue Buchungen
   nicht mehr auswählbar sein.
2. **Gültigkeitsspanne** (`fromDate`/`untilDate` bzw. `validFrom`/`validUntil`) — zeitgesteuerte
   Einschränkung: das Objekt ist außerhalb seines Gültigkeitszeitraums nicht buchbar.

Bisher fehlte eine explizite Regel, wie diese Filter in Create- vs. Edit-Dialogen angewendet werden,
und wie sichergestellt wird, dass ein Edit-Dialog immer den aktuell gespeicherten Wert anzeigen kann.

## Considered Options

* Keine formale Regel; jeder Dialog löst das ad hoc
* Einheitliche Regel: in Edit-Dialogen immer den gespeicherten Wert einschließen, in Create-Dialogen
  nur aktive Objekte zeigen

## Decision Outcome

Chosen: **Einheitliche Regel**, weil sie Datenkonsistenz garantiert (kein versehentliches Überschreiben
eines gespeicherten Werts) und gleichzeitig die Dropdowns in Create-Dialogen kompakt hält.

### Consequences

* Good: Edit-Dialoge verlieren nie den aktuell gespeicherten referenzierten Wert, unabhängig davon,
  ob dieser inzwischen hidden oder abgelaufen ist.
* Good: Create-Dialoge zeigen nur nutzbare Objekte — keine veralteten Einträge.
* Good: Beide Mechanismen (`hide` und Gültigkeit) werden konsistent angewendet.
* Neutral: Die Dropdown-Befüllung im Controller muss zwischen Create- und Edit-Pfad unterscheiden
  (Parameter `isEdit` bzw. den aktuell gespeicherten ID-Wert einschließen).

---

## Das `hide`-Flag: Semantik und Zweck

Das `hide`-Flag ist ein **UX-Archivierungsmechanismus**. Es markiert ein Stammdatum als veraltet —
nicht mehr für neue Buchungen oder Zuweisungen geeignet — ohne es zu löschen.

| Eigenschaft | Beschreibung |
|---|---|
| Persistenz | Entität bleibt vollständig in der Datenbank |
| Bestehende Referenzen | bleiben gültig; Buchungen auf das Objekt bleiben erhalten |
| Neue Referenzen (Create) | ausgeschlossen — das Objekt erscheint nicht im Dropdown |
| Neue Referenzen (Edit) | ausgeschlossen, **außer** wenn das Objekt bereits auf dem bearbeiteten Datensatz gespeichert ist |
| Listansicht | eigener Toggle „Versteckte anzeigen" (`showHidden`), damit Manager Hidden-Objekte sehen und pflegen können |
| Entitäten mit `hide` | `Customer`, `Employee`, `Employeecontract`, `Customerorder`, `Suborder` |
| Entitäten ohne eigenes `hide` | `Employeeorder` — erbt Sichtbarkeit von `Suborder` und `Customerorder` |

### Wann `hide` setzen?

Ein Objekt wird auf `hide = true` gesetzt, wenn es dauerhaft aus dem aktiven Betrieb ausscheidet,
aber historisch referenziert bleibt. Beispiele:

- Ein Mitarbeiter verlässt das Unternehmen → `Employee.hide = true`
- Ein Kundenauftrag ist vollständig abgerechnet und wird nicht mehr benötigt → `Customerorder.hide = true`
- Eine Berichtsvorlage ist durch eine neue Version ersetzt worden → `ReportDefinition.hide = true`

---

## Gültigkeitsspannen: Semantik und Zweck

Viele Stammdaten besitzen eine zeitliche Gültigkeitsspanne (`fromDate`/`untilDate` oder
`validFrom`/`validUntil`). Ein Objekt ist *aktuell gültig*, wenn das heutige Datum innerhalb dieser
Spanne liegt. Außerhalb der Spanne ist das Objekt abgelaufen (oder noch nicht aktiv).

| Eigenschaft | Beschreibung |
|---|---|
| Prüfbedingung | `untilDate` ist `null` **oder** `untilDate >= heute` |
| Abgelaufene Objekte in Dropdowns | werden wie hidden behandelt (ausgeblendet) |
| Edit-Dialog | abgelaufener aktuell gespeicherter Wert bleibt sichtbar (gleiche Regel wie bei `hide`) |
| Abgrenzung zu `hide` | `hide` ist eine explizite manuelle Entscheidung; Gültigkeit ist automatisch zeitgesteuert |
| Entitäten mit Gültigkeitsspanne | `Customerorder`, `Suborder`, `Employeeorder`, `Employeecontract`, `AuthorizationRule` |

---

## Dropdown-Filterregel: Create vs. Edit

Dies ist die zentrale Verhaltenregel für alle Formular-Dropdowns, die auf Stammdaten zeigen:

| Kontext | Filterregel |
|---|---|
| **Create-Dialog** | Zeige nur aktuell gültige (`untilDate ≥ heute` oder `null`) **und** nicht-hidden (`hide ≠ true`) Einträge |
| **Edit-Dialog** | Wie Create, **plus** schließe den aktuell auf dem Datensatz gespeicherten Wert immer ein — unabhängig von `hide` und Gültigkeit |

Implementierungshinweis (Controller): Der Edit-Pfad kennt den gespeicherten Fremdschlüssel. Nach dem
Aufbau der gefilterten Liste wird geprüft, ob der gespeicherte Wert enthalten ist. Fehlt er, wird er
der Liste hinzugefügt, ohne die übrigen Filter zu lockern.

---

## Implementierungsregeln

### DAO-Schicht

Die beiden Filter sind als unabhängige JPA-`Specification`-Prädikate umzusetzen:

```java
// Nie notHidden() innerhalb von showOnlyValid() bündeln — sie sind orthogonale Belange.
if (!TRUE.equals(showHidden))   predicates.add(notHidden().toPredicate(root, query, builder));
if (!TRUE.equals(showInvalid))  predicates.add(showOnlyValid().toPredicate(root, query, builder));
```

`showOnlyValid()` prüft nur das Enddatum:

```java
builder.or(
    builder.isNull(root.get(Entity_.untilDate)),
    builder.greaterThanOrEqualTo(root.get(Entity_.untilDate), today())
)
```

### Service- / Controller-Schicht

- Dropdown-Befüllung im Controller: immer `isEdit` und die aktuell gespeicherte ID übergeben,
  damit der Edit-Pfad den Wert bei Bedarf ergänzen kann.
- Listen-Views: `show` (Gültigkeit) und `showHidden` als unabhängige Boolean-Toggle-Parameter;
  Session-Keys nach Schema `<modul>.<entität>.show` / `<modul>.<entität>.showHidden`.

#### Stored-ID-Muster: gespeicherte Fremdschlüssel über Roundtrips hinweg sichern

HTMX-Change-Handler setzen abhängige Felder auf `null` zurück (z. B. `form.setSuborderId(null)` in
`change-customerorder`), und Validierungsfehler können dazu führen, dass ein Select-Element ohne
passende Option keinen Wert übermittelt. In beiden Fällen ist die ursprünglich gespeicherte ID im
Form-Objekt verloren und `addFormModel()` kann den Wert nicht mehr zur Liste ergänzen.

**Lösung:** Das Form-Objekt erhält ein `storedXxxId`-Feld, das einmalig beim Laden des Edit-Formulars
aus der Entität befüllt wird und danach nie mehr verändert wird. Es wird als
`<input type="hidden">` im Template gerendert und überlebt so jeden POST-Roundtrip.
`addFormModel()` fällt auf dieses Feld zurück, wenn das primäre Feld `null` ist.

```java
// 1. Im Form-Objekt: separates "gespeichertes" Feld
public class EmployeeorderForm {
    private Long suborderId;
    /** Einmalig aus der DB gesetzt; wird von HTMX-Handlern nie zurückgesetzt. */
    private Long storedSuborderId;
    // ...
}

// 2. In toForm(): beide Felder gemeinsam initialisieren
private EmployeeorderForm toForm(Employeeorder eo) {
    form.setSuborderId(eo.getSuborder().getId());
    form.setStoredSuborderId(eo.getSuborder().getId());
    // ...
}

// 3. In addFormModel(): storedId als Fallback, wenn das primäre Feld null ist
Long suborderIdToEnsure = form.getSuborderId() != null
    ? form.getSuborderId()
    : form.getStoredSuborderId();
if (isEdit && suborderIdToEnsure != null
        && filteredSuborders.stream().noneMatch(so -> Objects.equals(so.getId(), suborderIdToEnsure))) {
    Suborder suborderToAdd = suborderService.getSuborderById(suborderIdToEnsure);
    // Nur hinzufügen, wenn der Unterauftrag zum aktuell gewählten Kundenauftrag gehört
    if (suborderToAdd != null
            && Objects.equals(suborderToAdd.getCustomerorder().getId(), form.getOrderId())) {
        filteredSuborders.add(suborderToAdd);
    }
}
```

```html
<!-- 4. Im Template: hidden input direkt neben dem id-Feld -->
<input type="hidden" th:field="*{id}" />
<input type="hidden" th:field="*{storedSuborderId}" />
```

**Wann dieses Muster anwenden?** Immer dann, wenn

- ein Fremdschlüssel-Feld durch einen HTMX-Change-Handler zurückgesetzt werden kann, **oder**
- ein Select-Element beim erneuten Rendern nach einem Validierungsfehler keinen passenden Eintrag
  findet und deshalb keinen Wert übermittelt.

Das Muster ist nicht nötig, wenn weder HTMX-Handler das Feld zurücksetzen noch
Validierungsfehler-Roundtrips den Wert verlieren können.

### Entitäten ohne eigenes `hide`-Flag

`Employeeorder` hat kein eigenes `hide`. Der `notHidden()`-Filter prüft das Flag der übergeordneten
Entitäten via Join:

```java
builder.and(
    builder.notEqual(suborderJoin.get(Suborder_.hide), TRUE),
    builder.notEqual(customerorderJoin.get(Customerorder_.hide), TRUE)
)
```

---

## Verwandte Entscheidungen

- ADR-0011: Klassifizierung der Entitäten in Stammdaten und Bewegungsdaten
- Issue #624: Edit-Dialog — aktuell gespeicherte referenzierte Objekte müssen im Dropdown sichtbar sein
