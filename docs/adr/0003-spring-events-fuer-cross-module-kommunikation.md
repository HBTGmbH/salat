# ADR-0003 Spring Events für Cross-Module-Kommunikation

Date: 2026-05-24
Status: Accepted

## Context and Problem Statement

Im Modular Monolith (ADR-0001) müssen Module gelegentlich auf Aktionen anderer Module reagieren — zum Beispiel muss das `order`-Modul prüfen, ob ein Kunde gelöscht werden darf, obwohl die Löschlogik im `customer`-Modul liegt. Direkte Service-zu-Service-Aufrufe über Modulgrenzen würden Zyklen erzeugen und die Modul-Unabhängigkeit untergraben.

## Considered Options

* Direkte Service-zu-Service-Aufrufe (inter-modul)
* Shared-Kernel: gemeinsame Interfaces in `common`
* Spring Application Events (publish/subscribe)

## Decision Outcome

Chosen: **Spring Application Events**, weil sie die Kopplung umkehren (das publizierende Modul kennt die Subscriber nicht) und sich nahtlos in Spring Boot integrieren. Für blockierende Vetoes gegen destruktive Operationen gibt es ein spezialisiertes `VetoableEvent`-Muster.

### Consequences

* Good: keine direkten Abhängigkeiten zwischen Modulen für Seiteneffekte
* Good: neue Reaktionen auf ein Event können hinzugefügt werden, ohne den Publisher zu ändern
* Good: Vetoes (z. B. "Kunde kann nicht gelöscht werden, weil noch Aufträge existieren") sind über `VetoedException` sauber modelliert
* Bad: Ausführungsreihenfolge von Listenern ist nicht garantiert (ohne explizite `@Order`)
* Bad: Fehler in einem Listener können schwerer zu debuggen sein als direkte Aufrufe
* Neutral: Spring Events sind synchron per Default; asynchrone Verarbeitung erfordert `@Async`

## Implementierungsmuster

```
// Publisher (z. B. CustomerService)
publisher.publishEvent(new CustomerDeleteEvent(customerId));

// Listener (z. B. im order-Modul)
@EventListener
public void on(CustomerDeleteEvent e) {
    if (orderDao.existsForCustomer(e.getId()))
        throw new VetoedException(...);
}
```

Event-Hierarchie: `VetoableEvent` → `DomainObjectDeleteEvent` / `DomainObjectUpdateEvent` in `common/event/`.
