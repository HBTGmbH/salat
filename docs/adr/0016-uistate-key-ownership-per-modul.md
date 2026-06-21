# ADR-0016 UiState-Key-Ownership pro Modul

Date: 2026-06-21
Status: Accepted

## Context and Problem Statement

ADR-0014 führte `UiState` und `UiStateKey` als zentralen Mechanismus für benutzerbezogenen Selektionszustand ein. Die ursprüngliche Implementierung definierte alle Schlüsselkonstanten (`SELECTED_CONTRACT`, `SELECTED_CUSTOMER`, `SELECTED_ORDER`, `SELECTED_SUBORDER`) und das HTTP-Param-Mapping als statische Member in `UiStateKey` im `common`-Modul.

Das `common`-Modul hat dadurch Kenntnis von domänenspezifischen Konzepten aus `employee` (Mitarbeitervertrag) und `order` (Kunde, Auftrag, Unterauftrag). Das verstößt gegen die Regel, dass `common` keine Abhängigkeiten zu anderen `org.tb.*`-Modulen haben darf. Außerdem muss jede neue UiState-Dimension eine Änderung in `common` erfordern, was das Modul unnötig koppelt.

## Considered Options

* **Option A** — Status quo: alle Keys und Param-Mappings bleiben als statische Konstanten in `common/UiStateKey`.
* **Option B** — Contributor-Muster: `UiStateKey` wird ein `@Component`, das alle `UiStateKeyContributor`-Implementierungen (eine pro Modul) über Spring DI einsammelt.
* **Option C** — Keys per Konfigurationsdatei (z. B. YAML-Property) registrieren.

## Decision Outcome

Chosen: **Option B**, weil sie die Kopplung von `common` zu Domänenmodulen vollständig entfernt, die Java-Typsicherheit beibehält und keine Laufzeit-Konfiguration benötigt.

### Implementierung

**`UiStateKeyContributor`** (`common/web/UiStateKeyContributor.java`)
- Interface; liefert `Map<String, String> getParamToKeyMappings()` (HTTP-Parametername → interner Schlüssel).

**`UiStateKey`** (`common/web/UiStateKey.java`)
- Wird von einer `final`-Utility-Klasse zu einem `@Component`.
- Injiziert `List<UiStateKeyContributor>` und aggregiert deren Mappings in `getParamToKey()`.
- Enthält keine Domänenkonstanten mehr.

**`UiState`** (`common/web/UiState.java`)
- Convenience-Methode `getSelectedContractId()` entfernt (war die einzige domänenspezifische Methode).
- Neue Methode `getAll()` liefert eine unveränderliche Kopie aller gespeicherten Werte, so dass Konsumenten (z. B. die Fehlerseite) über alle Keys iterieren können, ohne sie zu kennen.

**`UiStateFilter`** (`common/filter/UiStateFilter.java`)
- Nimmt jetzt `UiStateKey uiStateKey` als zweiten Konstruktor-Parameter.
- Ruft `uiStateKey.getParamToKey()` statt der statischen Map auf.

**`EmployeeUiStateKeyContributor`** (`employee/controller/EmployeeUiStateKeyContributor.java`)
- Trägt `"employeeContractId" → "selectedContract"` bei.
- Exponiert `SELECTED_CONTRACT` als `public static final String` für Controller im `dailyreport`-Modul.

**`OrderUiStateKeyContributor`** (`order/controller/OrderUiStateKeyContributor.java`)
- Trägt `"customerId" → "selectedCustomer"`, `"orderId" → "selectedOrder"`, `"suborderId" → "selectedSuborder"` bei.
- Exponiert die entsprechenden Konstanten.

**`error.html`** / **`ErrorPageController`**
- `ErrorInfo`-Record verwendet jetzt `Map<String, Long> uiState` statt vier einzelner Felder.
- Das Template iteriert generisch über `errorInfo.uiState.entrySet()` — keine hardcodierten Schlüssel mehr.

### Consequences

* Good: `common` hat keine Abhängigkeit zu `employee` oder `order`
* Good: neue UiState-Dimensionen lassen sich durch Hinzufügen eines Contributor-Beans im jeweiligen Modul einführen — `common` bleibt unverändert
* Good: Fehlerseite zeigt alle gesetzten UiState-Werte generisch an, unabhängig von der Anzahl der Contributor
* Bad: Key-Konstanten sind über mehrere Module verteilt; ein Aufrufer muss die Contributor-Klasse des richtigen Moduls importieren
* Neutral: `UiStateFilter` erhält jetzt eine Singleton-Abhängigkeit (`UiStateKey`) zusätzlich zur bereits bestehenden Request-scoped-Abhängigkeit (`UiState`)
