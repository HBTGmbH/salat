# ADR-0044 GraphQL-Schnittstelle mit Spring for GraphQL: Resolver als Controller, Regel im Service

Date: 2026-10-25
Status: Accepted

## Context and Problem Statement

Maschinelle Aufrufer, also Skripte, Werkzeuge, Integrationen und KI-Agenten, sollen genau die Daten
holen, die sie brauchen, und fachliche Änderungen auslösen. Die bestehenden REST-Endpunkte
(`/api/dailyreports`, `/api/workingday`, `/api/employee-orders`, …) sind verstreut. Jeder beschreibt
sich über OpenAPI einzeln, und jede neue Frage braucht einen neuen Endpunkt.

Das Konzept [`docs/ideen/graphql-konzept.md`](../ideen/graphql-konzept.md) beschreibt eine
GraphQL-Schnittstelle, und ein Spike (#1532, Konzept Abschnitt 10) hat die offenen technischen
Fragen beantwortet:

- **Laufzeitkontext:** Resolver und Batch-Loader laufen im Thread der Anfrage. `AuthorizedUser`,
  `AuthorizedEmployee` und Open-in-View tragen dort. Auf einem fremden Thread fehlen sie, und das
  scheitert erst beim Zugriff, nicht beim Start.
- **Aspekte:** `AuthorizationAspect` greift an Resolver und Service.
- **Grenzwerte:** Die Tiefengrenze 8 schließt die Standard-Introspection der Werkzeuge aus, die Tiefe
  15 hat.
- **Services:** Sie reichten an einigen Stellen nicht, etwa ohne Version, ohne ids beim Anlegen oder
  mit Entitäten in der Signatur.

Zu entscheiden ist, mit welcher Technik und nach welchen Regeln die Schnittstelle gebaut wird.

## Considered Options

* **A — Spring for GraphQL**, Schema-first, Resolver als `@Controller`-Klassen je Modul
* **B — Netflix DGS**, eine Schicht über Spring for GraphQL mit Codegenerierung
* **C — Viaduct** (Airbnb), Tenant Modules mit Zusammensetzung über den Graphen
* **D — ein aus dem Datenmodell erzeugtes Schema** (Hasura, PostGraphile, Elide …)

## Decision Outcome

Chosen: **A — Spring for GraphQL**.
- **Bewährt und ohne Fremdframework:** Es steht im Boot-BOM, `@Authorized` und beide Aspekte wirken
  ohne Umbau, und Schema je Modul, `extend type`, Batch-Laden und Grenzwerte als Bohnen sind im
  Spike erprobt.
- **Gegen B:** DGS bringt vor allem Codegenerierung und Federation, beides braucht Salat nicht.
  Dafür kostet es ein Maven-Plugin aus der Community.
- **Gegen C:** Viaduct ist zu jung, an Gradle gebunden und für einen einzelnen Monolithen zu groß.
- **Gegen D:** Ein erzeugtes Schema umgeht genau die Services, an die die Aufrufe gehen sollen.

Dazu gelten diese Regeln:

1. **Ein Resolver ist ein Controller.** Er liegt im Unterpaket `graphql` seines Moduls, ist dünn und
   ruft Services, bevorzugt des eigenen Moduls. Für Aufrufe in fremde Module gelten die Regeln von
   ADR-0021: keine Entität über die Modulgrenze.
2. **Keine Entität im Schema.** Die Typen entstehen aus DTOs und Records, Bezüge zu fremden
   Objekten sind Verweistypen (`PersonRef`, `SuborderRef`).
3. **Die Regel lebt im Service.** Jede Methode, die ein Feld auflöst, trägt `@Authorized` mit
   derselben Anforderung wie der Controller der entsprechenden Seite. Wer was sieht, entscheidet der
   Service, nicht der Resolver.
4. **Resolver laufen synchron:** kein `Callable`, `Future`, `CompletableFuture`, `Mono` oder
   `Flux`.
5. **Module schneiden das Schema.** Jedes Modul bringt seine eigene `.graphqls`-Datei unter
   `classpath:graphql/` mit. Wer eine Kante definiert, bestimmt die erlaubte Importrichtung
   (`extend type`).
6. **Das Schema ist die Dokumentation und ein Vertrag.**
   - Jeder Typ, jedes Feld und jedes Argument hat eine deutsche Beschreibung, die Feldnamen sind
     englisch.
   - Das Schema wächst nur durch Hinzufügen. Was wegfallen soll, bekommt `@deprecated`.
   - Der veröffentlichte Stand liegt in `docs/graphql-schema.graphqls`.
7. **Grenzwerte:**
   - Tiefe und Komplexität gelten für fachliche Abfragen. Reine Introspection begrenzt
     `GoodFaithIntrospection` von graphql-java.
   - Dazu ein Zeitlimit je Anfrage und eine Begrenzung der Anfragen je Login und Minute.
8. **Ändern mit Version, Anlegen mit Idempotenzschlüssel.**
   - Eine Änderung nennt die Version, in der gelesen wurde. Ein Konflikt antwortet mit `XX-0003`.
   - Ein Anlegen nennt einen Schlüssel, eine Wiederholung legt nichts zweites an. Der Schlüssel liegt
     in derselben Transaktion wie das Angelegte.
9. **Jeder Aufruf geschieht im Namen einer Person**, mit dem Token der bestehenden zustandslosen
   Kette unter `/api`. Die Schnittstelle ist für alle angemeldeten Personen offen, mit ihren Rechten.
   In den Umgebungen trägt dort auch die Browser-Sitzung: Easy Auth setzt für jede Anfrage mit
   Sitzungs-Cookie den Token-Header, und die Kette liest ihn. Eine übernommene Anmeldung der
   Oberfläche gilt unter `/api` nicht.
10. **Nur `POST` mit `application/json`.** Die Kette unter `/api` hat kein CSRF-Token. Gegen eine
    Mutation von einer fremden Seite schützt, dass der Endpunkt nichts anderes annimmt: ein Formular
    oder eine einfache Cross-Site-Anfrage (`text/plain`, `application/x-www-form-urlencoded`, `GET`)
    kommt mit 415 bzw. ohne Ausführung zurück. `GraphqlIntegrationTest` hält das fest. GET oder ein
    weiterer Content-Type darf nur mit einem anderen Schutz dazukommen.
11. **GraphiQL in allen Umgebungen**, unter `/graphiql`, verlinkt im Footer. Es ist das eingebaute
    GraphiQL von Spring for GraphQL und nutzt die Sitzung der angemeldeten Person. Der Not-Aus ist
    `SPRING_GRAPHQL_GRAPHIQL_ENABLED=false`, ohne Release. Das Restrisiko steht unten und ist
    ausdrücklich in Kauf genommen.

### Consequences

* **Good:** Ein Endpunkt `/api/graphql` beschreibt sich selbst über Introspection. Werkzeuge und
  Agenten erschließen ihn ohne eigene Doku.
* **Good:** Dieselbe Regel gilt von beiden Eingängen. Die Buchungsliste über GraphQL und die Seite
  rufen dieselbe Methode mit derselben Sichtbarkeit.
* **Good:** Die Lücken, die der Spike gezeigt hat, sind in den Services geschlossen, und die
  Oberfläche profitiert mit: Version im `TimereportDTO`, ids beim Anlegen, buchbare Unteraufträge als
  Werte.
* **Bad:** Eine zweite HTTP-Grenze will gepflegt sein. Jede Stammdatenart, die dazukommt, braucht
  vorher eine Leseregel im Service (Konzept 2.4, Stufe 3a).
* **Bad:** GraphQL antwortet mit HTTP 200 und meldet Fehler in `errors`. Die Abbildung der
  `ErrorCode`s steht deshalb an einer zweiten Stelle neben `AuthorizationExceptionHandler`
  (`GraphqlExceptionResolver`).
* **Bad, in Kauf genommen: GraphiQL lädt fremdes JavaScript** (Regel 11).
  - Springs Seite lädt React, GraphiQL und die Editor-Worker zur Laufzeit von esm.sh.
  - Die Integrity-Hashes im Import-Map decken nur die Einstiegsmodule ab. Das sind Zweizeiler, die auf
    `…/es2022/*.bundle.mjs` weiterverweisen; der eigentliche Code und die Worker haben keinen Hash.
  - Der Code läuft auf dem Origin von Salat mit der Sitzung der Person, die `/graphiql` öffnet. Er
    erreicht `/api` über den Easy-Auth-Header und die Oberfläche über das lesbare CSRF-Cookie, und
    Salat setzt keine Content-Security-Policy.
  - Die Wahrscheinlichkeit ist gering, der Schaden groß (Vergleichsfall polyfill.io, 2024).
    Eingedämmt wird es durch den Not-Aus per Umgebungsvariable. Die Versionen pinnt Spring for
    GraphQL und hebt sie mit seinen Updates.
  - Verworfen:
    - **GraphiQL 4.1.0 als Webjar (UMD):** ältere Hauptversion und zusätzliche Pflege.
    - **GraphiQL 5 über mvnpm und esbuild:** ein neues Build-Werkzeug.
    - **Das Webjar von GraphiQL 5.2.1:** nicht auflösbar (`clsx` 1.x gegen 2.x) und ohne Bundler im
      Browser nicht ladbar.
    - **Vollständige Hashes, CSP oder Sandbox:** etwa so viel Aufwand wie Selbstausliefern, dazu
      fragil.
* **Neutral:** Die REST-Endpunkte bleiben. Ob sie abgelöst werden, entscheidet ihre Nutzung im
  Produktionsbericht.

## Pros and Cons of the Options

### B — Netflix DGS
* Good: Codegenerierung hält Schema und Java-Typen deckungsgleich, guter Testclient.
* Bad: Codegenerierung nur über ein Community-Plugin für Maven, Versionen laufen Spring for GraphQL
  hinterher, und Netflix rät vom Mischen mit Spring-Resolvern ab.

### C — Viaduct
* Good: Modulgrenzen sind Teil des Programmiermodells.
* Bad: Version 1.0 erst seit Mai 2026, Gradle-Plugin, Kotlin als Laufzeitabhängigkeit, kein
  Spring-Starter.

### D — Erzeugtes Schema
* Good: kein Code für Resolver.
* Bad: geht an Services, Berechtigung und fachlichen Regeln vorbei und macht Tabellen zum Vertrag.
