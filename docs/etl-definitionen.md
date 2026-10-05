# ETL-Definitionen pflegen

Die ETL-Definitionen stehen in der Tabelle `etl_definition` und werden von Hand in der Datenbank
gepflegt. Die Anwendung bietet dafür keine Oberfläche; sie liest die Definitionen nur und führt sie
aus (Liste unter „System → ETL-Läufe", REST-Schnittstelle, nächtlicher Lauf).

## Spalten

| Spalte | Inhalt |
|---|---|
| `name` | Pflicht und eindeutig (#1207). Unter diesem Namen wird die Definition angezeigt, im Formular gewählt und über die REST-Schnittstelle angesprochen. |
| `description` | Freitext für die Auswahlliste. |
| `reference_period` | `YEAR`, `QUARTER`, `MONTH`, `WEEK` oder `DAY` — in welche Abschnitte der Zeitraum eines Laufs zerlegt wird. |
| `init`, `execute`, `cleanup` | Die SQL-Blöcke, die je Abschnitt laufen. |

## Abhängigkeiten

Eine Definition, die die Ergebnisse einer anderen braucht, hängt von ihr ab. Seit #1207 steht jede
Abhängigkeit als Zeile in `etl_definition_dependency`, mit den **ids** beider Definitionen — nicht
mit ihren Namen:

```sql
-- "umsatz" hängt von "stunden" ab: stunden läuft vorher
INSERT INTO etl_definition_dependency (etl_definition_id, depends_on_id)
SELECT abhaengig.id, vorher.id
  FROM etl_definition abhaengig, etl_definition vorher
 WHERE abhaengig.name = 'umsatz' AND vorher.name = 'stunden';
```

- Die Fremdschlüssel weisen eine Zeile ab, deren Definition es nicht gibt.
- **Eine Umbenennung ist gefahrlos.** Sie ändert weder die Reihenfolge eines Laufs noch, ob er
  durchgeht. Vor #1207 musste jede Abhängigkeit, die den alten Namen nannte, von Hand nachgezogen
  werden, und eine vergessene ließ den ganzen Lauf „alle Definitionen" scheitern.
- Wird eine Definition gelöscht, verschwinden ihre eigenen Abhängigkeiten mit ihr. Hängt noch eine
  andere Definition von ihr ab, weist die Datenbank das Löschen ab.
- Ein Zyklus lässt den Lauf weiterhin scheitern, mit Meldung in seiner Zeile.
