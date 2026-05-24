# ADR-0009 Liquibase für Datenbankmigrationen

Date: 2026-05-24
Status: Accepted

## Context and Problem Statement

Schemaänderungen müssen reproduzierbar, versioniert und idempotent auf alle Umgebungen (lokal, Staging, Produktion) angewendet werden. Manuelle SQL-Skripte sind fehleranfällig und schwer nachzuverfolgen.

## Considered Options

* Manuelle SQL-Skripte
* Flyway
* Liquibase

## Decision Outcome

Chosen: **Liquibase**, weil es idempotente Migrationen über `preConditions` unterstützt (kritisch für eine wachsende Codebasis, in der Migrationen gelegentlich auf bereits existierenden Strukturen aufsetzen), YAML lesbarer als Flyway-SQL für strukturelle Änderungen ist und sich nahtlos in Spring Boot integriert.

### Consequences

* Good: `preConditions: onFail: MARK_RAN` verhindert Fehler, wenn eine Spalte/Tabelle bereits existiert (z. B. bei manuellen Hotfixes in Produktion)
* Good: eine einzige Changelog-Datei gibt einen vollständigen Überblick über die Schemahistorie
* Bad: Boolean-Spalten müssen als `bit(1)` deklariert werden — Hibernate mappt `Boolean` auf `bit`, nicht auf `boolean`/`tinyint`; fehlerhafte Typen führen zu stiller Schema-Validation-Fehler
* Bad: existierende Changesets dürfen nie editiert werden; Korrekturen erfordern ein neues Changeset

## Konventionen

**Datei:** `src/main/resources/db/changelog/db.changelog-master.yaml` — einzige Changelog-Datei; immer anhängen, nie editieren.

**Pflichtstruktur je Changeset:**
```yaml
- changeSet:
    id: <eindeutige-id>
    author: <Initialien>  # z. B. kr
    preConditions:
      onFail: MARK_RAN
      columnExists:          # oder tableExists
        tableName: <tabelle>
        columnName: <spalte>
    changes:
      - addColumn:
          tableName: <tabelle>
          columns:
            - column:
                name: <spalte>
                type: bit(1)   # für Boolean; NICHT boolean oder tinyint
```

**Boolean-Regel:** `type: bit(1)` ist zwingend — Hibernate validiert das Schema gegen `bit`, andere Typen führen zu Startfehlern.
