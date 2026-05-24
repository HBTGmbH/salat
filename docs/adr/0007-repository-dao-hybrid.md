# ADR-0007 Repository + DAO Hybrid Pattern mit DTO-Rückgabe und Authorization-Filtering

Date: 2026-05-24
Status: Accepted

## Context and Problem Statement

Spring Data Repositories bieten generische CRUD- und Query-Methoden. Werden sie direkt aus Services und Controllern genutzt, entstehen drei Probleme:

1. **Persistence-Details** (Sortierung, Filterlogik) sickern in die aufrufenden Schichten durch.
2. **LazyInitializationException**: JPA-Entities mit lazy-geladenen Assoziationen lösen außerhalb einer Transaktion (z. B. im Thymeleaf-Template) Fehler aus.
3. **Authorization-Leaks**: Aufrufer erhalten möglicherweise Datensätze, auf die der aktuelle Nutzer keinen Lesezugriff hat — sofern nicht jeder Aufrufer selbst filtert.

## Considered Options

* Direkte Repository-Nutzung, Rückgabe von Entities, Authorization im Service oder Controller
* DAO-Wrapper mit Rückgabe von Entities, Authorization im Aufrufer
* DAO-Wrapper mit Rückgabe von DTOs und zentralem Authorization-Filter im DAO

## Decision Outcome

Chosen: **DAO-Wrapper mit DTO-Rückgabe und zentralem Authorization-Filter**, weil der DAO damit drei Verantwortlichkeiten in einer einzigen Konvertierungs-Pipeline bündelt: Authorization prüfen, gelöschte Datensätze ausblenden und Entities in transaktionsunabhängige DTOs überführen. Kein Aufrufer muss sich darum kümmern.

### Consequences

* Good: Authorization-Filtering ist garantiert — kein Aufrufer kann es vergessen oder umgehen
* Good: keine `LazyInitializationException` — alle Assoziationen sind beim Verlassen des DAOs aufgelöst
* Good: DTOs definieren explizit, welche Daten das JPA-Modell nach außen gibt
* Good: Sortierfelder über JPA-Metamodel (`Timereport_.sequencenumber`) — Tippfehler zur Compile-Zeit erkannt
* Bad: eine zusätzliche Klasse (DTO) pro Anwendungsfall
* Neutral: wenn für ein Modul kein DAO existiert, darf das Repository ausnahmsweise direkt verwendet werden

## Muster: toDaoList als zentrale Konvertierungs-Pipeline

Jede öffentliche Lesemethode eines DAOs gibt ihr Ergebnis durch `toDaoList` (bzw. `toDao` für Einzelwerte). Die Methode ist der einzige Ort, an dem Authorization, Deleted-Filter und Entity→DTO-Mapping zusammenkommen:

```java
// Öffentliche Methode — gibt immer DTOs zurück, nie Entities
public List<TimereportDTO> getTimereportsByDate(LocalDate date) {
    return toDaoList(timereportRepository.findAll(
        where(reportedAt(date)).and(notDeleted()).and(orderedBySequencenumber())
    ));
}

// toDaoList: Authorization + Deleted-Filter + Mapping in einer Pipeline
private List<TimereportDTO> toDaoList(List<Timereport> timereports) {
    return timereports.stream()
        .filter(this::accessible)           // nur lesend berechtigte Datensätze
        .filter(not(Timereport::isDeleted)) // gelöschte ausblenden
        .map(this::toDao)                   // Entity → DTO (alle Assoziationen auflösen)
        .collect(Collectors.toList());
}

private boolean accessible(Timereport timereport) {
    return timereportAuthorization.isAuthorized(timereport, AccessLevel.READ);
}
```

Die Autorisierungsprüfung delegiert an eine modulspezifische `*Authorization`-Klasse (`TimereportAuthorization`, `EmployeeAuthorization`, …), die intern `AuthorizedUser` verwendet.

## DTO-Struktur

`toDao` löst alle Assoziationen innerhalb der Transaktion auf und erzeugt ein flaches, unveränderliches DTO. `TimereportDTO` (`dailyreport/domain/TimereportDTO.java`) aggregiert Felder aus fünf Entities:

```java
private TimereportDTO toDao(Timereport timereport) {
    return TimereportDTO.builder()
        .id(timereport.getId())
        // Employee (referenziert über Employeecontract)
        .employeeName(timereport.getEmployeecontract().getEmployee().getName())
        .employeeSign(timereport.getEmployeecontract().getEmployee().getSign())
        // Customer (referenziert über Suborder → Customerorder)
        .customerShortname(timereport.getSuborder().getCustomerorder().getCustomer().getShortname())
        .customerorderSign(timereport.getSuborder().getCustomerorder().getSign())
        // Suborder
        .suborderDescription(timereport.getSuborder().getShortdescription())
        // ...
        .build();
}
```

Der Klassen-Javadoc von `TimereportDTO` hält fest: `Timereport`-Entities dürfen ausschließlich innerhalb von `TimereportService` verwendet werden — nach außen nur als `TimereportDTO`.

## Beteiligte Klassen (Beispiel: dailyreport-Modul)

`org.tb.dailyreport.persistence.TimereportDAO`, `TimereportRepository`, `dailyreport/domain/TimereportDTO.java`, `dailyreport/auth/TimereportAuthorization.java`
