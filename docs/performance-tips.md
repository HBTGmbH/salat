# Performance Tips

Praxisregeln aus konkreten Messungen an Salat. Jeder Abschnitt beschreibt ein Muster, das
auf Testdaten unsichtbar ist und erst mit Produktionsdatenmengen auffällt.

Wie gemessen wird: Profil `local-qa` (produktionsnahe Antwortzeiten) und das Overlay
`sqltrace` (jedes Statement mit Laufzeit) — siehe [ADR-0020](adr/0020-local-qa-profil-fuer-performancemessungen.md)
und den Abschnitt „Measuring response times locally" in der [README](../README.md).

Faustregel für die Interpretation: **Nicht die Dauer einzelner Statements zählt, sondern ihre
Anzahl.** Ein Request mit 768 Statements, von denen keines länger als 20 ms braucht, ist
langsam wegen der 768 Roundtrips — kein Index und kein `OPTIMIZE TABLE` behebt das.

---

## 1. Konvertierte Attribute brauchen `equals`/`hashCode`

**Regel:** Jede Klasse, die über einen `AttributeConverter` auf eine Spalte gemappt wird, muss
`equals` und `hashCode` mit Wertesemantik implementieren.

Hibernate entscheidet per `equals` gegen eine Kopie des Ladezustands, ob eine Entity schmutzig
ist. Fehlt die Wertesemantik, greift Identitätsvergleich, der Vergleich schlägt immer fehl, und
die Entity gilt bei **jedem** Flush als geändert.

Gemessener Fall: `UserPreference.settings` ist per `@Convert(UserPreferenceConverter.class)` auf
`UserPreferenceMap` gemappt. Die Klasse war unveränderlich, hatte aber kein `equals`. Folge:
**90 `UPDATE`-Statements pro GET-Request auf das Dashboard**, ohne dass irgendetwas geändert
wurde. Nach Ergänzen von `equals`/`hashCode`: 0.

Verschärfend kommt hinzu: Mit `open-in-view: true` lebt der Persistence Context über das
komplette Rendering, und jede Query in einer nicht-`readOnly`-Transaktion löst einen Auto-Flush
aus. Eine dauerhaft „schmutzige" Entity erzeugt damit ein UPDATE pro Query im Request.

**Prüfen:** Neue `AttributeConverter` immer zusammen mit `equals`/`hashCode` auf dem Domain-Typ
einführen. Ein fehlendes `equals` bricht keinen Test — es kostet nur stillschweigend Schreibzugriffe.

## 2. `@ManyToOne` über `@JoinTable` ist EAGER und erzeugt N+1

`@ManyToOne` ist ohne `fetch`-Angabe **EAGER**. Geht die Assoziation über eine `@JoinTable`,
löst Hibernate sie nicht per Join auf, sondern mit einem Sekundär-Select **pro Zeile**.

Gemessener Fall: `Employee.salatUser` (`@ManyToOne` + `@JoinTable`). Ein `findAll()` über 194
Mitarbeiter erzeugte 194 zusätzliche `select … from salat_user where id=?`. Bei zwei Aufrufen
pro Request: 389 Statements.

**Lösung:** Repository-Methode mit explizitem Join-Fetch statt `findAll()`:

```java
@Query("SELECT e FROM Employee e LEFT JOIN FETCH e.salatUser")
List<Employee> findAllWithSalatUser();
```

`LEFT JOIN FETCH` statt `JOIN FETCH`, sonst fallen Zeilen ohne Assoziation stillschweigend raus —
das ist eine Verhaltensänderung, kein Performance-Fix.

Die Assoziation stattdessen auf `LAZY` zu stellen hilft **nicht**, wenn der Code sie ohnehin für
jede Zeile dereferenziert: dann entstehen dieselben N Selects, nur später.

## 3. Autorisierung nicht nach dem Laden in Java filtern

Muster: alle Zeilen laden, danach per Java-Stream auf die sichtbaren filtern
(`findAll().stream().filter(auth::isAuthorized)`). Die Datenbank liefert Zeilen, die verworfen
werden, und wenn das Autorisierungsprädikat eine Assoziation dereferenziert, kommt N+1 dazu
(siehe 2.).

Zu finden u. a. in `EmployeeService.getLoginEmployees`, `EmployeecontractDAO`, `EmployeeDAO`,
`EmployeeorderDAO`, `TimereportDAO`. Neue Abfragen sollten das Prädikat in die Query ziehen.

## 4. ViewHelper werden pro Render mehrfach aufgerufen

Thymeleaf ruft dieselbe Getter-Methode während eines Renders beliebig oft auf. Ein ViewHelper
ohne Puffer macht daraus ebenso viele Queries.

Gemessener Fall: `EmployeeContractSelectorViewHelper.getSelectedContractId()` löste 90
`getCurrentContract`-Queries pro Request aus. Die Nachbarmethode `getViewableContracts()`
pufferte bereits — der Puffer fehlte nur an einer Stelle.

**Regel:** ViewHelper sind `@Scope(SCOPE_REQUEST)`; jeder DB-Zugriff darin gehört in ein
Feld gepuffert. Dabei nur den teuren Zugriff puffern, nicht die gesamte Methode, damit
Vorrangregeln (z. B. `UiState` vor DB-Fallback) erhalten bleiben.

## 5. Stammdaten-Lookups gehören vor die Schleife, nicht in sie

**Regel:** Wird für jede Zeile einer Ergebnismenge ein Stammdatensatz nachgeschlagen, muss die
Nachschlagetabelle einmal geladen und im Speicher aufgelöst werden — nicht per Query pro Zeile.

Betroffen ist jede Methode nach dem Muster `findEffective…(key, date)`: ein Aufruf pro Zeitbuchung,
und wegen der Fallback-Hierarchie bis zu drei Statements pro Aufruf. Auf `order_pricing` mit 283
Zeilen ist die Tabelle klein — die Zeilenzahl, die dagegen aufgelöst wird, ist es nicht.

Gemessener Fall: `/budget/dashboard` löste über 30 aktive Budgetpläne 13.318 Zeitbuchungen auf und
erzeugte damit **37.359 `select … from order_pricing`** von insgesamt 38.871 Statements. Ersetzt
durch `OrderPricingLookup`/`EmployeeCostLookup` (einmal laden, `Map` nach Schlüssel, Datumsfilter im
Speicher): 1 Statement.

Die Fallback-Hierarchie und der Datumsfilter wandern damit aus JPQL nach Java — beides gehört
getestet, sonst verschiebt der Umbau stillschweigend die Semantik. Voraussetzung ist, dass sich
Gültigkeitszeiträume nicht überlappen; das erzwingen die `checkNoOverlap`-Prüfungen beim Speichern.

## 6. Pro-Zeile-Services in einen Batch-Aufruf zusammenfassen

Ein Service, der eine einzelne Entity auswertet, lädt seine Nachbardaten selbst. Über eine Liste
aufgerufen, multipliziert das jede dieser Ladungen mit der Listenlänge.

Gemessener Fall: `computeUtilizationInfo(budget)` lud Kundenauftrag, Unteraufträge und
Zeitbuchungen. Das Dashboard rief es je aktivem Budgetplan auf — bei mehreren Plänen auf demselben
Auftrag dieselben Daten mehrfach. `computeUtilizationInfos(List)` teilt sie jetzt pro Auftragsschlüssel
und lädt den Pricing-Lookup einmal für alle.

**Regel:** Neben die Einzelmethode eine Batch-Variante stellen, die die gemeinsamen Ladevorgänge
außerhalb der Schleife erledigt, und Listen-Aufrufer darauf umstellen. Die Einzelmethode bleibt für
Einzelfälle (z. B. `BudgetAlertService`) bestehen und delegiert auf denselben Rechenkern, damit
Einzel- und Batch-Pfad nicht auseinanderlaufen.

## 7. Was Datenbank-Wartung nicht behebt

`ANALYZE TABLE` und `OPTIMIZE TABLE` wurden auf dem Produktionsdatenabzug gemessen:

| | Statements | SQL-Zeit | Antwortzeit (n=30) |
|---|---|---|---|
| Ausgangslage | 768 | 828 ms | 1,654 s ± 0,075 |
| nach `ANALYZE` + `OPTIMIZE` | 768 | 622 ms | 1,654 s ± 0,075 (keine Änderung) |
| nach den Code-Fixes 1–4 | 205 | 272 ms | **0,558 s ± 0,028** |

`OPTIMIZE TABLE` senkte die reine SQL-Zeit, bewegte die Antwortzeit aber **nicht** — der Request
hing nicht daran, wie schnell MySQL antwortet, sondern an der Anzahl der Roundtrips. Erst die
Code-Änderungen wirkten.

Kleine Stichproben täuschen: Bei einer Streuung von sd ≈ 0,4 s sind 12 Messungen zu wenig, um
eine Verbesserung von 6 % von Rauschen zu unterscheiden. Mindestens 30 Messungen nach Warmlauf,
und Mittelwert immer mit Standardfehler angeben.

`/budget/dashboard` nach den Fixes 5–6, gleiche Methodik:

| | Statements | Antwortzeit |
|---|---|---|
| Ausgangslage | 38.871 | 30,086 s ± 0,643 (n=10) |
| nach den Code-Fixes 5–6 | 1.245 | **1,057 s ± 0,005** (n=30) |

Hier reichten 10 Messungen für die Ausgangslage: bei sd ≈ 2 s ist ein Faktor 28 kein Rauschen.
Das ist die Ausnahme, nicht die Regel — je kleiner der erwartete Effekt, desto näher an n=30.

## 8. Lokale Verfälschungsfaktoren

* Der `testdb`-Container läuft mit `innodb_buffer_pool_size=128M` — bei ~485 MB
  Produktionsdaten der größte lokale Störfaktor.
* Ohne Profil `local-qa` fehlen Asset-Caching und Thymeleaf-Template-Cache, und der
  Autorisierungs-Cache läuft im Sekundentakt statt stündlich ab.
* Die ersten Requests nach dem Start messen JIT-Compilation, nicht die Anwendung.
