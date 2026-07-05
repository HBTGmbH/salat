# ADR-0019 Direkter Repository-Zugriff in Services (kein DAO für neue Module)

Date: 2026-07-05
Status: Accepted

Supersedes ADR-0007.

## Context and Problem Statement

ADR-0007 hat das Repository + DAO Hybrid Pattern eingeführt, bei dem ein DAO-Wrapper
zwischen Repository und Service geschaltet ist. Das Muster entstand aus drei Problemen
der damaligen Architektur (Authorization-Leaks, LazyInitializationException, Persistenz-Details
in aufrufenden Schichten).

Die DAOs in Salat sind historisch gewachsen: Sie stammen aus der Struts-Ära, in der Salat
kein Spring-MVC-Framework mit durchgängigem Transaktionsmanagement hatte. In dieser Zeit
musste der DAO Authorization-Filtering und Entity-zu-DTO-Konvertierung übernehmen, weil der
Service keinen sicheren, transaktionsgebundenen Kontext garantierte.

Heute gelten diese Randbedingungen nicht mehr:

- **Authorization** wird in zwei Ebenen durchgesetzt: `@PreAuthorize` auf dem Controller und
  `@Authorized` auf dem `@Service`. Ein separater DAO-Filter ist redundant.
- **Transaktionsmanagement** ist durch `@Transactional` auf dem Service garantiert. Lazy-Assoziationen
  können innerhalb der Service-Methode sicher aufgelöst werden. LazyInitializationException ist kein
  Architekturproblem mehr, sondern ein Fetch-Strategy-Problem, das am Entity gelöst wird.
- **Persistence-Details** (Sortierung, Filterung) gehören in Repository-Methoden via `@Query` oder
  JPA Specifications — nicht in einen DAO-Wrapper, der das Repository ohnehin nur durchreicht.

Der DAO erzeugt für neue Module reinen Overhead: eine zusätzliche Klasse ohne eigene Logik, die
lediglich Repository-Aufrufe delegiert.

## Considered Options

* DAO-Wrapper beibehalten (ADR-0007)
* Repository direkt im Service verwenden, DAO entfällt für neue Module
* Repository direkt im Service + explizites Service-DTO statt Entity-Rückgabe

## Decision Outcome

Chosen: **Repository direkt im Service verwenden; DAO entfällt für neue Module**,
weil der DAO unter modernem Spring kein eigenständiges Problem mehr löst und der Code
dadurch schlanker und einfacher nachvollziehbar wird.

Service-DTOs (Records) bleiben als Eingabe-Objekte für Schreiboperationen (`*Data`-Records)
erhalten. Für einfache Lesemethoden dürfen Entities als Rückgabetyp verwendet werden,
sofern sie innerhalb derselben Transaktion vollständig initialisiert werden.

### Consequences

* Good: weniger Dateien und Schichten pro Feature
* Good: Repository-Methoden sind der einzige Ort für Persistence-Details (kein DAO-Duplikat)
* Good: Authorization liegt sauber auf Service-Ebene via `@Authorized`
* Bad: bestehende DAOs (`TimereportDAO`, `CustomerorderDAO`, …) werden nicht migriert —
  sie bleiben als Legacy-Schicht bestehen und werden nur bei ohnehin fälligen Refactorings
  aufgelöst
* Neutral: das `accessRepositoriesOnlyInServicesOrDAOs`-ArchUnit-Regel gilt weiterhin;
  sie erlaubt bereits direkten Repository-Zugriff aus Services

## Regeln für neue Module

1. **Kein DAO** — Services importieren das Repository direkt.
2. **Repository-Methoden** kapseln alle Persistence-Details (Sortierung, Filterung, `@Query`).
3. **`@Authorized` auf dem Service** deckt Authorization ab; kein Authorization-Filtering im Repository.
4. **Bestehende DAOs** werden nicht aktiv gelöscht; neue Features in Legacy-Modulen können
   den bestehenden DAO erweitern, müssen es aber nicht.
