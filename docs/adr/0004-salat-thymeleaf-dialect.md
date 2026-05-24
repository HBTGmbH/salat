# ADR-0004 Salat Custom Thymeleaf Dialect

Date: 2026-05-24
Status: Superseded by ADR-0005

## Context and Problem Statement

Formular-Felder in Thymeleaf-Templates wurden über `th:replace`-Fragment-Calls eingebunden (z. B. `th:replace="~{fragments/form-fields :: textInput}"`). Das erzeugte verbose, schwer lesbare Templates und war fehleranfällig bei Attribut-Übergaben. Es bestand Bedarf an einer saubereren, IDE-freundlichen Abstraktion für wiederverwendete Leaf-Komponenten.

## Considered Options

* `th:replace` Fragment-Calls (Status quo)
* Web Components / Custom Elements (erfordert clientseitiges JS)
* Custom Thymeleaf Dialect mit eigenen Element-Prozessoren

## Decision Outcome

Chosen: **Custom Thymeleaf Dialect (`salat:`)**, weil Thymeleaf Dialects nativ unterstützt werden, keine clientseitige Infrastruktur benötigen und attribute-basierte Tags (`<salat:textInput th:field="..." />`) deutlich lesbarer sind als Fragment-Calls.

### Consequences

* Good: Templates sind kürzer und lesbarer
* Good: IDE-Unterstützung über den registrierten Namespace (`xmlns:salat="..."`)
* Good: Implementierung bleibt im Backend — kein JavaScript-Overhead
* Bad: Custom Dialect ist projektspezifisch; neue Entwickler müssen ihn kennenlernen
* Bad: Debugging von Processor-Code ist aufwändiger als plain Thymeleaf-Fragments
* Neutral: `th:replace`-Fragments bleiben für strukturelles Layout (z. B. `master-table`, Layout-Dekoratoren) gültig; der `salat:`-Dialekt zielt auf Leaf-Komponenten

## Registrierte Tags

`salat:form`, `salat:inputs`, `salat:buttons`, `salat:textInput`, `salat:textarea`, `salat:checkboxSwitch`, `salat:formButtons`

Implementierung: `org.tb.common.thymeleaf.processor`; Registrierung via `SalatDialect.getProcessors()`.
