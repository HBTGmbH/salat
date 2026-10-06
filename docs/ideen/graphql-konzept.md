# Konzept: GraphQL-Schnittstelle für Salat

Stand: 01.10.2026 · Status: **Entwurf**. Einzelentscheidungen sind getroffen (→ 9), das Konzept als Ganzes ist noch nicht bestätigt. Keine Umsetzung.

Dieses Dokument beschreibt eine GraphQL-Schnittstelle, über die Daten gelesen, geändert und gelöscht
werden. Alle Aufrufe gehen an die bestehenden Services, und es gelten dieselben Sichtbarkeits- und
Schreibregeln wie in der Oberfläche. Am Ende stehen drei Wege der technischen Umsetzung mit einer
Empfehlung. Die Entscheidung kommt erst in ein neues ADR, wenn das Konzept als Ganzes bestätigt ist.

---

## 1. Ziel und Abgrenzung

**Ziel.** Ein Endpunkt, über den maschinelle Aufrufer genau die Daten holen, die sie brauchen, und
fachliche Änderungen auslösen. Dazu gehört das **Anlegen, Ändern und Löschen aller
Geschäftsobjekte**, also Buchungen, Arbeitstage, Kunden, Aufträge, Unteraufträge,
Mitarbeiteraufträge, Personen und Verträge, nicht nur Buchungen. Er soll die verstreuten
REST-Endpunkte (`/api/employee-orders`, `/api/dailyreports`, `/api/workingday`, …)
mittelfristig ergänzen und eine einheitliche, selbstbeschreibende Schnittstelle bieten.

**Aufrufer** sind potenziell alle (entschieden 01.10.2026): Skripte Einzelner, Werkzeuge, eine
mobile Oberfläche, Integrationen und KI-Agenten. Der Entwurf geht deshalb von Aufrufern aus, die
das Schema nicht kennen und beliebige Abfragen bauen. Zwei Folgen davon:

- Die Schnittstelle muss sich selbst beschreiben (Introspection, 6.1).
- Sie muss sich gegen teure Abfragen selbst schützen (6.2), statt sich auf vorab bekannte Abfragen
  zu verlassen.

**Jeder Aufruf geschieht im Namen einer Person** (entschieden 01.10.2026), auch bei Integrationen.
Eine Integration hält ein Token, das zu einer Person gehört, und darf genau das, was diese Person
darf. Technische Konten ohne Person gibt es nicht. Ein Token aus einer reinen Anwendungsanmeldung
(Client Credentials) ohne zugeordnete Person wird abgewiesen. Damit bleibt `AuthorizedUser` die
einzige Quelle der Rechte, und jede Änderung ist im Protokoll einer Person zugeordnet.

**Nicht Ziel.**

- Kein Ersatz für die Thymeleaf-Oberfläche. Sie bleibt Spring MVC (ADR-0002).
- Kein generischer Datenbankzugang. Die Schnittstelle bildet fachliche Operationen ab, nicht
  Tabellen. Werkzeuge, die aus dem Datenmodell ein Schema erzeugen (Hasura, PostGraphile, Elide,
  pg_graphql …), scheiden deshalb aus: Sie umgehen genau die Services, an die die Aufrufe gehen
  sollen.
- Zunächst keine Subscriptions (Push).
- Keine Pflege von Berechtigungsregeln, keine übernommene Anmeldung (Impersonation) und keine
  ETL-Definitionen über die Schnittstelle (siehe 5.5).

---

## 2. Ausgangslage im Code

Was ein Entwurf vorfindet und berücksichtigen muss:

### 2.1 Authentifizierung

`/api/**` und `/rest/**` laufen über eine eigene, zustandslose Filterkette mit JWT als
Resource Server, ohne CSRF (`AzureEasyAuthSecurityConfiguration#restApi`, ADR-0026). Ein Endpunkt
`/api/graphql` fällt ohne weitere Konfiguration in diese Kette. `UiStateFilter` lässt diese Pfade aus.
Gemerkter Oberflächenzustand, und damit auch eine übernommene Anmeldung aus dem Cookie, wirkt dort
nicht. Für eine Maschinenschnittstelle ist das richtig so.

### 2.2 Zwei Ebenen der Autorisierung (ADR-0006)

- **Ebene 1, HTTP-Grenze:** `@Authorized(requires…)` am Controller, durchgesetzt von
  `AuthorizationAspect`.
- **Ebene 2, Service-Grenze:** `@Authorized` am Service, dazu ein Laufzeit-Guard im Methodenrumpf.
- Die Antwort auf eine Ablehnung kommt aus einer einzigen Stelle: `AuthorizationExceptionHandler`
  setzt 401 bzw. 403 als `ProblemDetail`. **GraphQL antwortet aber grundsätzlich mit HTTP 200 und
  meldet Fehler im Feld `errors`.** Das ist eine zweite Stelle, an der eine Ablehnung beantwortet
  wird, und genau dafür verlangt AGENTS.md eine bewusste Antwort (→ 6.3).

### 2.3 Sichtbarkeitsregeln

Je Modul gibt es Klassen, die beantworten, wer was sehen und schreiben darf:

| Klasse | Regel (gekürzt) | Angewandt in |
|---|---|---|
| `TimereportAuthorization` | Lesen: Management, People Lead für sein Team, Person selbst, Verantwortliche des Auftrags, Backoffice bei abrechenbaren Unteraufträgen, Regeln der Kategorie `TIMEREPORT`. Schreiben: abhängig vom Status des Zeitraums (offen / freigegeben / abgenommen, #1164, #1215) | `TimereportDAO` (Filter je Zeile), `TimereportService` |
| `TimereportVisibility` | dieselbe Leseregel als **Bedingung für eine Abfrage**, als Disjunktion von Klauseln, nicht als Kreuzprodukt (#1092) | `TimereportListDAO` |
| `EmployeeAuthorization`, `EmployeecontractAuthorization` | Management alles; Person selbst und People Lead für sein Team lesend; `LOGIN` über Regeln | `EmployeeService`, `EmployeecontractService`/`-DAO` |
| `EmployeeorderAuthorization` | Management alles; People Lead für sein Team lesend; sonst nur die eigenen | `EmployeeorderDAO` |
| `ReleaseAuthorization` | Freigabe und Abnahme, Regeln `RELEASE_TIMEREPORTS`, `ACCEPT_TIMEREPORTS` | `ReleaseService` |
| `BudgetAuthorization` | Budgetpläne und Controlling | Services des Moduls `budget` |
| `ReportAuthorization`, `ETLAuthorization` | Berichte, ETL | jeweilige Services |

Über allem liegen die feingranularen `AuthorizationRule`s (Kategorien `TIMEREPORT`, `WORKINGDAY`,
`EMPLOYEE`, `RELEASE_TIMEREPORTS`, `ACCEPT_TIMEREPORTS`, `REPORT_DEFINITION`, ETL) mit den
Zugriffsstufen `READ` ⊂ `WRITE` ⊂ `DELETE` sowie `EXECUTE` und `LOGIN`.

### 2.4 Die entscheidende Lücke: Lesen in den Stammdaten-Services

Für eine Schnittstelle, die „an die Services übergibt", ist dies der wichtigste Befund:

> **Die Lesemethoden von `CustomerService`, `CustomerorderService`, `SuborderService` und
> `CustomerSegmentService` prüfen nur die Anmeldung.** Ihr Klassen-`@Authorized` ist ohne
> Argument, und `@Authorized` ohne Argument heißt nur *authentifiziert*. Den Ausschluss von
> `RESTRICTED` übernimmt heute allein der Controller (`@Authorized(requireUnrestricted = true)`).
> AGENTS.md hält das für die Befehlspalette ausdrücklich fest: „Die Services der Fachmodule prüfen
> beim Lesen meist nur die Anmeldung, eingeschränkte Anmeldungen hält also der Anbieter fern."

**Das heutige Modell ist ein Zugang je Rolle, kein Filter je Datensatz.** Die Kunden- und
Auftragslisten sind je nach Rolle gar nicht sichtbar, nur lesend oder auch schreibend:

| Rolle | Zugang zu Kunden, Aufträgen, Unteraufträgen | umgesetzt durch |
|---|---|---|
| `RESTRICTED` | gar nicht sichtbar | Menü in `layout/base.html` ausgeblendet, Controller `@Authorized(requireUnrestricted = true)` |
| alle übrigen außer Management | nur lesend, und zwar **alle** Datensätze | Listen ohne Bedienelemente zum Ändern |
| Management | lesend und schreibend | `hasRole('MANAGER')` in den Listen, `requiresManager` an Controller und Service |

Innerhalb einer Stufe filtern weder Controller noch Service. Wer die Liste sieht, sieht alle
passenden Datensätze:

| Liste | Methode | Filter nach Berechtigung |
|---|---|---|
| Auftraggeber (`CustomerController`) | `CustomerService.getAllCustomerDTOsByFilter` | nein, nur `hide` und Suchtext |
| Aufträge (`CustomerorderController.list`) | `CustomerorderService.getCustomerordersByFilters` | nein, nur Zeitraum, `hide`, Kunde, Suchtext |
| Unteraufträge (`SuborderController`) | `SuborderService.getSubordersByFilters` | nein, ebenso |
| Mitarbeiteraufträge | `EmployeeorderDAO` | **ja**, über `EmployeeorderAuthorization` |

Aufträge und Unteraufträge zeigen erst etwas, wenn ein Suchtext oder Kunde gesetzt ist
(`filterSet ? … : List.of()`). Das ist eine Frage der Darstellung, keine Berechtigung.

Gibt eine GraphQL-Schicht diese Methoden ungeprüft weiter, sieht eine externe Person über die
Schnittstelle alle Kunden und Aufträge. Das ist genau die Lücke, die #919 für die Seiten
geschlossen hat. Daraus folgt Leitprinzip 3.4.

Auch die meisten bestehenden REST-Endpunkte tragen kein `@Authorized` an der Klasse
(Ausnahme: `JiraReplicationRestController`) und prüfen die Anmeldung von Hand.

### 2.5 Laufzeitkontext

- `AuthorizedUser` ist `@RequestScope` und liest den `SecurityContext` je Anfrage.
- `spring.jpa.open-in-view` ist `true`. Lazy-Assoziationen lassen sich also bis zum Ende der
  Anfrage nachladen, **aber nur im Thread der Anfrage**.
- GraphQL-Engines führen Felder teilweise asynchron aus (DataLoader, `CompletableFuture`). Ein
  Resolver auf einem anderen Thread sieht weder die Request-Scope-Bohne noch den
  `EntityManager` der Anfrage. Die Erfahrung aus den asynchronen Handlern des Projekts zeigt: Das
  scheitert nicht laut, sondern mit fehlender Anmeldung oder `LazyInitializationException`.
- `ConcurrentModificationAspect` übersetzt Versionskonflikte an jedem `@Service` in `XX-0003`
  (ADR-0033).

### 2.6 Die zweite Lücke: Prüfungen beim Anlegen stehen im Controller

Wer Stammdaten über die Schnittstelle anlegen will, stößt auf dasselbe Muster wie beim Lesen. Ein
großer Teil der fachlichen Prüfungen steht nicht im Service, sondern im Controller, als
`bindingResult.rejectValue(…)`:

| Controller | Prüfungen (`rejectValue`) | Beispiele |
|---|---|---|
| `SuborderController` | 16 | Pflichtfelder, Längen, Zeitraum |
| `CustomerorderController` | 16 | Zeichen vorhanden und **eindeutig**, Längen, Durchführungs- und Vertragsverantwortliche vorhanden, Datum, Stunden |
| `EmployeeController` | 10 | Pflichtfelder, Kürzel und Loginname, **doppelter Name bei neuen Personen** |
| `EmployeeorderController` | 9 | Zeitraum gültig, **Beginn nicht vor Vertrag und Unterauftrag**, Unterauftrag gesetzt, Stunden |
| `EmployeecontractController` | 8 | Pflichtfelder, Zeitraum |
| `CustomerController` | 6 | Pflichtfelder, Längen |

Ruft die Schnittstelle nur den Service, entstehen ein Auftrag mit doppeltem Zeichen, ein
Mitarbeiterauftrag vor Vertragsbeginn oder eine Person ohne Nachnamen.

Dazu kommen zwei Eigenheiten der Service-Methoden selbst:

- `EmployeeService.createOrUpdate` und `EmployeeorderService.create` nehmen eine **Entität**
  entgegen, die der Controller zusammengebaut hat. Bei einer neuen Person legt der Controller auch
  den `SalatUser` an.
- `CustomerService.createOrUpdate`, `SuborderService.create`, `EmployeeorderService.create` und
  `EmployeeService.createOrUpdate` **liefern nichts zurück**. Eine Mutation kann ohne die id des
  neuen Objekts weder antworten noch eine Wiederholung erkennen (6.5).

Daraus folgt Leitprinzip 3.5: Eine Prüfung, die für jeden Eingang gilt, gehört in den Service.

---

## 3. Leitprinzipien

### 3.1 Ein Resolver ist ein Controller

Die GraphQL-Schicht ist eine weitere HTTP-Grenze, nicht eine neue Schicht dahinter. Für sie gelten
dieselben Regeln wie für Controller: dünn, keine Geschäftslogik, Aufruf **eines Services des
eigenen Moduls**. Sie liegt je Modul in einem neuen Unterpaket `graphql` neben `controller` und
`rest`, und `ArchitectureTest` erhält dafür dieselben Regeln wie `controller`.

### 3.2 Keine Entität im Schema

Das Schema beschreibt eigene, stabile Typen. Sie entstehen aus den DTOs und Records der Services,
nicht aus JPA-Entitäten. Das hat drei Gründe:

- Eine Entität im Schema hängt ihren ganzen Assoziationsgraphen an die Schnittstelle. Jede
  Lazy-Assoziation wird zu einem Feld, das an der Sichtbarkeit vorbei nachlädt.
- ADR-0021 verlangt beim modulübergreifenden Lesen ohnehin eine kontrollierte Kopie aus Werten und
  ids.
- Das Schema ist ein Vertrag mit Fremden. Eine umbenannte Spalte darf ihn nicht brechen.

### 3.3 Sichtbarkeit gilt je Objekt, nicht je Einstieg

Ein Graph lädt zum Navigieren ein: `timereport → suborder → customerorder → timereports →
employee → contracts`. Wer nur am Einstiegspunkt prüft, gibt über die dritte Kante frei, was der
erste Schritt nie gezeigt hätte. Deshalb:

- **Jeder Objekttyp wird mit der Regel seines eigenen Moduls aufgelöst**, egal über welche Kante
  man zu ihm kommt. Wer eine Buchung sieht, sieht damit nicht die Person in vollem Umfang.
- **Kanten zu fremden Objekten liefern einen Verweistyp** (`PersonRef`: id, Kürzel, Name;
  `SuborderRef`: id, vollständiges Zeichen, Bezeichnung). Er enthält nur, was die Oberfläche an
  derselben Stelle ohnehin zeigt. Der volle Typ (`Employee`, `Employeecontract`) ist nur über
  seinen eigenen Einstieg erreichbar und wird dort geprüft.
- **Listen werden als Bedingung gefiltert, nicht Zeile für Zeile.** Für Buchungen gibt es das
  schon (`TimereportVisibility`). Wo ein anderes Modul große Listen liefert, ist das Vorbild
  dasselbe.
- **Summen brauchen eine eigene Regel.** `bookedMinutes` an einem Unterauftrag ist eine Summe über
  Buchungen, die die aufrufende Person einzeln vielleicht nicht sehen darf. Eine Summe ist nur
  dort ein Feld, wo die Oberfläche sie derselben Rolle zeigt (Auftragsverantwortliche, Management).
  Sonst würde die Summe über eine Hintertür freigeben, was die einzelnen Zeilen schützen.

### 3.4 Die Regel lebt im Service, nicht in der GraphQL-Schicht

Ebene 1 bekommt der Resolver: `@Authorized(requires…)`, **dieselbe Anforderung wie der Controller
der entsprechenden Seite**. Ebene 2 bleibt im Service. Eine Kopie einer Sichtbarkeitsregel in der
GraphQL-Schicht ist ein Fehler. Sie driftet, und das Driften gibt Daten frei (dieselbe Begründung
wie bei `TimereportListDAO`).

Daraus folgt eine **Freigabe je Typ**: Ein Typ kommt erst ins Schema, wenn sein Service beim Lesen
selbst prüft. Für Kunden, Aufträge, Unteraufträge und Mitarbeiteraufträge heißt das vorab: Die
Lesemethoden filtern nach **Zuständigkeit**, mit derselben Regel wie die Buchungsliste (→ 5.2).
Das schließt die Lücke aus 2.4 für jeden künftigen Aufrufer und nicht nur für GraphQL. Diese Arbeit
ist Teil des Vorhabens und keine Nebensache.

**Kein Feld ohne ausdrückliche Anforderung.** Jede Query- und Mutation-Methode trägt ein
`@Authorized` mit ausgeschriebener Anforderung. Ein Test lehnt eine Methode ohne Annotation ab,
analog zur Regel gegen `@PreAuthorize` in `ArchitectureTest`.

### 3.5 Mutationen sind fachliche Befehle

Es gibt kein generisches `update(entity)`. Jede Mutation entspricht einer Methode, die der Service
schon anbietet:

- **Eine Mutation ist ein Service-Aufruf und damit eine Transaktion.** Mehrere Mutationen in einem
  Dokument laufen nach der GraphQL-Spezifikation nacheinander, jede in ihrer eigenen Transaktion,
  und sind nicht gemeinsam atomar. Das wird dokumentiert, nicht umgangen.
- **Fachliche Regeln gehören in den Service** (entschieden 01.10.2026). Dieselbe Prüfung gilt von
  jedem Eingang. Die Prüfungen aus 2.6 wandern vorab in die Services, je Geschäftsobjekt bevor es
  über die Schnittstelle angelegt werden kann (Stufe 3b). Das ist derselbe Gedanke wie bei der
  Zuständigkeit: eine Regel, beide Eingänge. Die rund 65 Prüfungen werden dafür einzeln einer von
  zwei Arten zugeordnet:

  | Art | Beispiele aus dem Code | gehört in |
  |---|---|---|
  | **Fachliche Regel**: gilt, egal woher die Daten kommen | Auftragszeichen eindeutig (`signExists` in `CustomerorderController`); Mitarbeiterauftrag beginnt nicht vor Vertrag und Unterauftrag (`EmployeeorderController`); Durchführungs- und Vertragsverantwortliche gesetzt; neue Person ohne schon vorhandenen Namen; Pflichtfelder; Höchstlängen, die aus der Datenbank kommen | **Service**, als `ErrorCodeException` |
  | **Formatprüfung**: hängt daran, dass das Formular Text schickt | Datum als Text nicht lesbar (`validateDate`); Dauer wie `1:3x` (`validateDuration`) | **Controller / Formular**. In GraphQL erledigen das die typisierten Felder (`Date`, `Duration`) |

  **Fallstrick Feldbezug.** Heute markiert die Oberfläche das betroffene **Feld**, weil der
  Controller die Prüfung über `rejectValue` einem Feld zuordnet. Kommt der Fehler künftig aus dem
  Service, trägt die `ServiceFeedbackMessage` deshalb zusätzlich den Namen des Feldes, auf das sie
  sich bezieht. Der Controller ordnet sie damit wieder dem Feld zu, statt sie nur als allgemeine
  Meldung über dem Formular zu zeigen. GraphQL gibt denselben Feldnamen in `extensions.field` aus
  (6.3). Ohne diesen Bezug würde die Oberfläche durch den Umzug schlechter.
- **Anlegen nimmt Datenobjekte und liefert die id.** Jede anlegende Service-Methode nimmt ein DTO oder
  Record entgegen (keine Entität, 3.2) und gibt die id des neuen Objekts zurück. Was der Controller
  heute beim Anlegen zusätzlich erledigt, etwa den `SalatUser` einer neuen Person, gehört damit
  ebenfalls in den Service.
- **Jedes Anlegen ist wiederholbar** (6.5). Eine Wiederholung mit demselben Schlüssel legt nichts
  zweites an.
- **Optimistisches Sperren:** Änderungs-Inputs tragen die `version` (`updatecounter`), die der
  Aufrufer gelesen hat. Ein Konflikt kommt als `XX-0003` zurück (ADR-0033). Ohne `version` würde ein
  Skript fremde Änderungen stillschweigend überschreiben.
- **Prüfseiten-Prinzip für Freigabe und Abnahme** (ADR-0027, Nachtrag): Die Oberfläche lässt erst
  prüfen, dann bestätigen. Die Schnittstelle bildet das nach. Die Query `releaseReview` liefert den
  geprüften Zeitraum, und die Mutation `releaseTimereports` verlangt genau diesen Zeitraum
  (`reviewedBegin`/`reviewedEnd`), wie `ReleaseService.releaseTimereports` es heute schon tut. Wer
  freigibt, hat damit nachweislich gesehen, was er freigibt.

### 3.6 Löschen folgt der Klassifikation der Daten (ADR-0011, ADR-0012)

| Art | Mutation | Bedeutung |
|---|---|---|
| Stammdaten (Kunde, Auftrag, Unterauftrag, Person, Vertrag) | `set…Hidden(id, hidden)` | der Normalfall: aus Auswahllisten nehmen, nichts geht verloren |
| Stammdaten | `delete…(id)` | nur wo der Service es anbietet. Ein Veto eines anderen Moduls (`VetoedException`) kommt mit seinen Meldungen als Fehler zurück |
| Gültigkeitsgebundenes (`AuthorizationRule`, später Konditionen) | `end…(id)` | beendet zum heutigen Tag statt zu löschen |
| Bewegungsdaten (Buchung) | `deleteTimereport(id)` | Soft-Delete, wie heute; Statusregel des Zeitraums gilt |

### 3.7 Module schneiden das Schema

Jedes Modul bringt seine eigene Schema-Datei mit (`graphql/order.graphqls`,
`graphql/dailyreport.graphqls`, …). Die Engine fügt die Dateien beim Start zusammen. Wichtig dabei:
Die erlaubte Importrichtung bestimmt, wer eine Kante definieren darf.

- `dailyreport` darf `order` importieren, also definiert `dailyreport` sowohl `Timereport.suborder`
  als auch die Rückrichtung `Suborder.timereports`, als **Typerweiterung** (`extend type Suborder`)
  in seiner eigenen Datei.
- `order` weiß dadurch nichts von Buchungen. Die Modulgrenze bleibt so, wie `ArchitectureTest` sie
  verlangt, und es braucht keinen Command-Event.

---

## 4. Schema-Entwurf (Ausschnitt)

Die Feldnamen sind englisch, wie die bestehenden REST-Pfade und DTOs. Die Beschreibungen im Schema
sind deutsch (ADR-0010; entschieden 01.10.2026). Der Ausschnitt zeigt die Form, nicht den vollen
Umfang.

**Weiterentwicklung ohne versionierten Pfad** (entschieden 01.10.2026). Es gibt genau einen
Endpunkt `/api/graphql`, ohne `/v1`, `/v2`. Das Schema wächst nur durch Hinzufügen:

- Neue Typen, Felder und optionale Argumente sind jederzeit erlaubt.
- Ein Feld, das wegfallen soll, bekommt `@deprecated(reason: "…")` mit dem Ersatz in der
  Begründung. Es bleibt erhalten, bis die Nutzung im Protokoll (6.6, je Operation) auf null gefallen
  ist, mindestens aber über eine feste Frist.
- **Brechende Änderungen sind ausgeschlossen:** ein Feld entfernen oder umbenennen, einen Typ
  verengen, ein Argument zur Pflicht machen, die Bedeutung eines Feldes ändern. Ein Test vergleicht
  das Schema mit dem zuletzt veröffentlichten und schlägt bei einer brechenden Änderung fehl.
- Wo sich eine Bedeutung wirklich ändern muss, entsteht ein neues Feld mit neuem Namen. Das alte
  läuft über `@deprecated` aus.

```graphql
# --- gemeinsam (common) ---
scalar Date        # ISO-8601, Tag
scalar Duration    # ISO-8601, z. B. PT1H30M

type PersonRef { id: ID!, sign: String!, name: String! }

interface Node { id: ID! }

type PageInfo { page: Int!, size: Int!, total: Int! }

# --- auth / employee ---
type Query {
  "Die angemeldete Person mit ihrem laufenden Vertrag"
  me: Me!
}

type Me {
  person: PersonRef!
  roles: [Role!]!
  currentContract: Employeecontract
  "Worauf die Person an diesem Tag buchen darf"
  bookableSuborders(on: Date!): [BookableSuborder!]!
}

# --- order ---
extend type Query {
  customer(id: ID!): Customer
  customers(filter: CustomerFilter, page: PageInput): CustomerPage!
  customerorder(id: ID!): Customerorder
  customerorders(filter: CustomerorderFilter, page: PageInput): CustomerorderPage!
  suborder(id: ID!): Suborder
}

type Customerorder implements Node {
  id: ID!
  version: Int!
  sign: String!
  description: String!
  customer: Customer!
  validity: DateRange!
  hidden: Boolean!
  responsible: [PersonRef!]!
  suborders(showInactive: Boolean = false, showHidden: Boolean = false): [Suborder!]!
}

input CustomerorderFilter {
  text: String
  customerId: ID
  showInactive: Boolean = false   # ADR-0029: inaktiv = Ende vor heute
  showHidden: Boolean = false     # hide-Flag, unabhängig vom Zeitraum
}

# --- dailyreport (erweitert order, nicht umgekehrt) ---
extend type Suborder {
  "Nur die Buchungen, die die angemeldete Person sehen darf"
  timereports(from: Date!, until: Date!, page: PageInput): TimereportPage!
}

extend type Query {
  timereports(filter: TimereportFilter!, page: PageInput): TimereportPage!
  workingday(contractId: ID!, date: Date!): Workingday
  releaseReview(contractId: ID!, until: Date!): ReleaseReview!
}

type Timereport implements Node {
  id: ID!
  version: Int!
  date: Date!
  duration: Duration!
  taskDescription: String
  ticket: String
  status: ReportStatus!          # OPEN, COMMITTED, CLOSED
  employee: PersonRef!           # Verweis, nicht die volle Person (3.3)
  suborder: SuborderRef!
  "Ob die angemeldete Person sie ändern darf, dieselbe Antwort wie beim Speichern"
  writable: Boolean!
}

type TimereportPage { items: [Timereport!]!, pageInfo: PageInfo!, totalDuration: Duration! }

type Mutation {
  # Jede anlegende Mutation verlangt einen idempotencyKey (6.5).
  createTimereport(idempotencyKey: ID!, input: CreateTimereportInput!): TimereportPayload!
  updateTimereport(id: ID!, version: Int!, input: UpdateTimereportInput!): TimereportPayload!
  deleteTimereport(id: ID!): DeletePayload!

  upsertWorkingday(input: WorkingdayInput!): WorkingdayPayload!
  markNotWorked(contractId: ID!, date: Date!): WorkingdayPayload!

  releaseTimereports(contractId: ID!, reviewedBegin: Date!, reviewedEnd: Date!): ReleasePayload!
  acceptTimereports(contractId: ID!, reviewedBegin: Date!, reviewedEnd: Date!): ReleasePayload!
  reopenTimereports(contractId: ID!, from: Date!): ReleasePayload!

  # --- Stammdaten (Management); dasselbe Muster für jedes Geschäftsobjekt ---
  createCustomer(idempotencyKey: ID!, input: CustomerInput!): CustomerPayload!
  createCustomerorder(idempotencyKey: ID!, input: CustomerorderInput!): CustomerorderPayload!
  updateCustomerorder(id: ID!, version: Int!, input: CustomerorderInput!): CustomerorderPayload!
  setCustomerorderHidden(id: ID!, hidden: Boolean!): CustomerorderPayload!
  deleteCustomerorder(id: ID!): DeletePayload!
  createSuborder(idempotencyKey: ID!, input: SuborderInput!): SuborderPayload!
  copySuborder(idempotencyKey: ID!, id: ID!): SuborderPayload!
  createEmployeeorder(idempotencyKey: ID!, input: EmployeeorderInput!): EmployeeorderPayload!
  createEmployee(idempotencyKey: ID!, input: EmployeeInput!): EmployeePayload!
  createEmployeecontract(idempotencyKey: ID!, input: EmployeecontractInput!): EmployeecontractPayload!
  # … update…, set…Hidden, delete… je Objekt wie beim Auftrag
}

type TimereportPayload { timereport: Timereport, warnings: [Message!]! }
```

Einige Entscheidungen im Entwurf:

- **`writable`** ist das Gegenstück zu dem, was die Oberfläche je Tag und Buchung entscheidet
  (`TimereportAuthorization.isWriteAllowed`). Ein Aufrufer kann damit Bearbeiten nur dort anbieten,
  wo das Speichern auch durchgeht. Es stammt aus derselben Methode, nicht aus einer Kopie.
- **Payload-Typen statt nackter Objekte** lassen Platz für Warnungen. Die Services sammeln heute
  schon `ServiceFeedbackMessage`s mit Schweregrad `warning`, etwa bei Pausen und Arbeitszeitgrenzen.
- **`showInactive` und `showHidden`** heißen wie in der Oberfläche und bedeuten dasselbe (#950,
  ADR-0029). Beide sind getrennte Schalter, nie eine gemeinsame Bedingung.
- **Paginierung** mit Seite und Größe und einer festen Obergrenze für die Größe. Das passt zu
  `TimereportListDAO` (Zeilen plus Summen über alle Treffer). Cursor-Paginierung nach Relay
  wäre möglich, löst aber kein Problem, das Salat hat.
- **Personen werden über ihre id adressiert, nie über das Kürzel** (#968). Das Kürzel ist Anzeige.

---

## 5. Berechtigungsmodell

### 5.1 Zuordnung je Typ

| Typ / Operation | Ebene 1 (Resolver) | Ebene 2 (Service) | Stand im Service |
|---|---|---|---|
| `me`, `bookableSuborders` | authentifiziert (auch `RESTRICTED`, wie `DailyController`) | eigener Vertrag | vorhanden |
| `customer(s)`, `customerorder(s)`, `suborder(s)` lesen | `requireUnrestricted`, wie die Controller (Zugang, 5.3) | **Zuständigkeit (5.2)** (Umfang) | **fehlt: heute nur Anmeldung (2.4)** |
| Stammdaten anlegen, ändern, ausblenden, löschen: Kunde, Auftrag, Unterauftrag, Mitarbeiterauftrag, Person, Vertrag | `requiresManager` | `requiresManager` + Guard | Berechtigung vorhanden; **fachliche Prüfungen teils nur im Controller (2.6)** |
| `employeeorders` lesen | `requireUnrestricted`, wie der Controller | Zuständigkeit (5.2) | heute `EmployeeorderAuthorization` im DAO, enger als die Buchungsliste |
| `employee`, `employeecontract` lesen | `requireUnrestricted` | `EmployeeAuthorization`, `EmployeecontractAuthorization` | vorhanden; bewusst **nicht** über Zuständigkeit erweitert (5.2) |
| `timereports` lesen | authentifiziert | `TimereportVisibility` / `TimereportAuthorization` | vorhanden |
| Buchung anlegen, ändern, löschen | authentifiziert | Statusregel des Zeitraums (#1164, #1215) | vorhanden |
| Arbeitstag | authentifiziert | `WorkingdayService.isWriteAllowed`, Regel `WORKINGDAY` | vorhanden |
| Freigabe / Abnahme / Wiederöffnen | authentifiziert / `requiresPeopleLead` | `ReleaseAuthorization` | vorhanden |
| Budget, Controlling | wie `BudgetController` | `BudgetAuthorization` | vorhanden, spätere Stufe |
| Rechnungen | `requiresBackoffice` | `requiresBackoffice` | vorhanden, spätere Stufe |

„Ebene 1 authentifiziert" heißt nicht „offen": Wo die Regel zeilenweise gilt, entscheidet der
Service. Die Annotation am Resolver hält nur fern, wer die Operation grundsätzlich nicht bekommt.

### 5.2 Lesen nach Zuständigkeit, eine Regel für Buchungen und Stammdaten

**Jeder sieht nur, wofür er zuständig ist.** Die Buchungsliste setzt das schon um
(`TimereportVisibilityService`, #1092). Dieselbe Regel trägt auch die Stammdaten, und zwar nicht
als zweite, „analoge" Regel, sondern als **dieselbe Zuständigkeit, auf andere Typen projiziert**.

**Die Zuständigkeit** wird einmal je Anfrage gebaut, eine Disjunktion von Klauseln wie heute:

| Rolle / Quelle | Klausel | Herkunft heute |
|---|---|---|
| Management | alles, keine Bedingung | `TimereportVisibility.all()` |
| jede Person | eigene Person | `Clause.forEmployees(eigene id)` |
| People Lead | die geführten Personen, auch mit abgelaufenem Vertrag | `getTeamEmployeeIdsIncludingExpired` |
| Durchführungs- und Vertragsverantwortliche | die verantworteten Aufträge (`responsibleHbt` **oder** `respEmpHbtContract`) | `getIdsByResponsibleEmployeeId` |
| Backoffice | fakturierbare Unteraufträge (`invoice = 'Y'`) | `Clause.billable()` |
| Regeln der Kategorie `TIMEREPORT` | Person, Auftrag, Unterauftrag oder Kombination (`xx:1453`) | `toClause` |

Jede Klausel spricht über vier Dimensionen: Person, Auftrag, Unterauftrag, fakturierbar. **Alle vier
sitzen auf dem Mitarbeiterauftrag** (`employeecontract.employee`, `suborder.customerorder`,
`suborder`, `suborder.invoice`). `TimereportListDAO.findFilterValues` nutzt das schon heute (#1127):
Die Werte der Filter sind genau die Mitarbeiteraufträge unter der Sichtbarkeit, eingeschränkt auf
die bebuchten. Ohne diese Einschränkung ist das die Sichtbarkeit der Stammdaten:

| Typ | sichtbar, wenn eine Klausel trifft auf … |
|---|---|
| `Timereport` | die Buchung selbst (unverändert) |
| `Employeeorder` | den Mitarbeiterauftrag selbst |
| `Suborder` | den Unterauftrag selbst, wenn die Klausel keine Person nennt; sonst einen Mitarbeiterauftrag dieser Person darauf |
| `Customerorder` | den Auftrag selbst (verantwortet, Regel auf den Auftrag) oder einen sichtbaren Unterauftrag |
| `Customer` | einen sichtbaren Auftrag |

Was das je Rolle ergibt:

- **Mitarbeitende** sehen die Aufträge, auf die sie eingeplant sind oder waren, also das, worauf sie
  buchen, und den Kunden dazu.
- **People Leads** sehen zusätzlich, worauf ihr Team eingeplant ist, also genau die Aufträge, die in
  den Buchungen ihres Teams vorkommen.
- **Durchführungsverantwortliche** sehen ihre Aufträge vollständig: alle Unteraufträge und alle
  Mitarbeiteraufträge darauf, auch solche ohne Buchung. **Vertragsverantwortliche**
  (`respEmpHbtContract`) gelten dabei genauso, wie in der Buchungsliste (`findIdsByResponsibleEmployee`).
  Es gibt keine zweite Antwort auf dieselbe Frage (entschieden 01.10.2026).
- **Backoffice** sieht die fakturierbaren Unteraufträge, die Aufträge, die mindestens einen davon
  haben (dieselbe Bedingung wie `getInvoiceableCustomerorders`), und deren Kunden.
- **Management** sieht alles.
- **Eingeschränkte Anmeldungen** dürfen die Schnittstelle nutzen (entschieden 01.10.2026). Sie
  brauchen keinen eigenen Fall: Sie bekommen nur die eigene Klausel und sehen damit ihre eigenen
  Einsätze, nicht mehr. Sie buchen über die Schnittstelle, wie sie es in der Oberfläche tun.

**Die Zusage, die das sicher macht:** *Was über eine sichtbare Buchung erreichbar ist, ist auch
direkt sichtbar, und nichts darüber hinaus.* Jede Buchung hängt an einem Mitarbeiterauftrag mit
derselben Person und demselben Unterauftrag. Eine Klausel, die die Buchung deckt, deckt damit auch
den Mitarbeiterauftrag, den Unterauftrag, den Auftrag und den Kunden. Damit liefert
`customerorder(id)` nie `null` für einen Auftrag, den eine sichtbare Buchung nennt. Den bestehenden
Konsistenztest (`TimereportVisibility.covers` gegen `isAuthorized`) ergänzt ein zweiter, der genau
diese Zusage für echte Daten festhält. Bekannte Ausnahme sind ein paar Altbuchungen, deren
Unterauftrag von dem ihres Mitarbeiterauftrags abweicht, innerhalb desselben Auftrags (Javadoc von
`findFilterValues`). Der Auftrag bleibt sichtbar, der abweichende Unterauftrag unter Umständen nicht.

**Bewusst nicht erweitert:**

- **Personen und Verträge** bleiben bei `EmployeeAuthorization` und `EmployeecontractAuthorization`
  (die Person selbst, People Lead für das Team, Management). Durchführungsverantwortliche und Backoffice
  sehen die Personen auf ihren Aufträgen als `PersonRef` (Kürzel, Name), wie in der Buchungsliste,
  aber keine Verträge, Urlaubsansprüche oder Überstunden. Zuständig für einen Auftrag zu sein heißt
  nicht, für die Menschen darauf zuständig zu sein.
- **Geld** (Stundensätze, Budgets, Kosten, Umsätze) bleibt bei `BudgetAuthorization`. Einen Auftrag
  zu sehen heißt nicht, seine Konditionen zu sehen.
- **`hide`** ist keine Frage der Sichtbarkeit. Es bleibt der getrennte Schalter `showHidden`.

**Zeitbezug.** Die Stammdaten fragen die Zuständigkeit ohne Zeitraum ab (`anyTime()`, wie die Werte
der Filter). Ein abgelaufener Einsatz und eine früher gültige Regel zählen also mit. Sonst wäre der
Auftrag einer Buchung aus dem Vorjahr nicht mehr auffindbar, obwohl die Buchung selbst sichtbar ist
(ADR-0029, #1106: ein Filter über Vorhandenes läuft nicht mit seinem Stammdatensatz ab).

**Modulschnitt** (entschieden 01.10.2026). Der Baustein, der die Zuständigkeit baut, liegt heute in
`dailyreport`. Das Modul `order` darf `dailyreport` nicht importieren, könnte seine eigenen Listen
damit also nicht filtern. Alles, was der Baustein braucht, liegt aber schon in `order`, `employee` und
`auth`. Deshalb zieht er nach `order` um, und `dailyreport` baut seine `TimereportVisibility` daraus. Das ist die erlaubte
Richtung. Die Regeln der Kategorie `TIMEREPORT` liest dann `order` über `AuthService`, und das
Format der Regelobjekte (`xx:1453`) steht weiterhin an einer Stelle. Diese Stelle ist dann der
Baustein in `order`, und `TimereportAuthorization.objectsOf` hält der bestehende Konsistenztest
dagegen.

**Abfragen.** Eine Disjunktion über mehrere Dimensionen liest bei einer großen Tabelle alles, die
Lehre aus #1127. Die Stammdaten-Abfragen gehen deshalb wie dort über den Mitarbeiterauftrag, mit
`exists` statt Join, und das Ergebnis sind ids. Die Tabellen sind klein gegen `timereport`. Vor der
Freigabe wird trotzdem gemessen, gegen den lokalen Abzug, n ≥ 30.

### 5.3 Die Oberfläche zieht mit (entschieden 01.10.2026)

Heute regelt die Rolle nur den **Zugang** zu einer Liste, also nicht sichtbar, lesend oder
schreibend (2.4). Wer lesen darf, sieht alle Datensätze. Künftig gilt die Zuständigkeit für
Oberfläche und Schnittstelle gleichermaßen. Es gibt genau eine Antwort auf „wer sieht welchen
Auftrag", denn zwei Antworten wären das Driften, vor dem 3.4 warnt.

**Zwei Achsen, die sich nicht vermischen:**

| Achse | Frage | entschieden durch | Wirkung |
|---|---|---|---|
| **Zugang** | Gibt es diese Liste oder Operation für mich, und darf ich schreiben? | Rolle: `@Authorized` an Controller, Resolver und Service, Menü und Bedienelemente | bleibt, wie sie ist |
| **Umfang** | Welche Datensätze stehen darin? | Zuständigkeit (5.2), im Service | **neu**: bisher „alle", künftig „wofür ich zuständig bin" |

- **Die drei Stufen bleiben.** Wer heute schreibend zugreift (Management), sieht weiterhin alles.
  Wer heute lesend zugreift, sieht künftig nur noch seinen Umfang. Wer heute keinen Zugang hat,
  bekommt in der Oberfläche auch keinen.
- **Eingeschränkte Anmeldungen** haben in der Oberfläche weiterhin keine Kunden- und
  Auftragslisten. Über die Schnittstelle bekommen sie die Einstiege, die sie zum Buchen brauchen
  (`me`, `bookableSuborders`, die eigenen Buchungen), mit ihrem Umfang. Das ist eine Frage des
  Zugangs je Eingang, nicht eine zweite Regel für den Umfang.
- **Schreiben** hängt allein am Zugang (`requiresManager`). Der Umfang schränkt das Schreiben nicht
  zusätzlich ein, weil das Management immer alles sieht.

**Dieselbe Regel, von beiden Grenzen aus gerufen.** Controller und GraphQL-Resolver sind zwei
Eingänge zu denselben Daten. Beide rufen dieselbe anzeigende Methode des Services, und diese wendet
dieselbe Zuständigkeit aus dem Baustein in `order` an. Die gemeinsame Sicht ist damit keine Absicht,
die man pflegen muss, sondern folgt aus der Bauart:

```
Controller ─┐
            ├─► CustomerorderService.getReadable…  ─►  Zuständigkeit (order)
Resolver ───┘
```

- **Der Eingang wählt, die Regel bleibt an einer Stelle.** Controller und Resolver entscheiden nur,
  dass sie für eine Person *anzeigen*, und rufen deshalb die anzeigende Methode. Wer was sieht,
  steht weder im Controller noch im Resolver.
- **Keine Kopie im Controller.** Eine Bedingung, die der Controller selbst zusammensetzt, müsste der
  Resolver wiederholen, und genau diese Wiederholung driftet.
- Die Befehlspalette (`PaletteProvider`) ist ein dritter Eingang derselben Art und ruft dieselbe
  Methode. Sie zeigt damit nichts, was die Liste nicht zeigt (ADR-0031).

Das ist eine **fachliche Änderung der Oberfläche** und kein Nebeneffekt der Schnittstelle. Sie
bekommt deshalb ein eigenes Vorhaben vor Stufe 3 (→ 8) und wird nicht mit GraphQL gebündelt.
Mitarbeitende sehen danach in den Listen der Kunden, Aufträge, Unteraufträge und Mitarbeiteraufträge
nur noch ihre Einsätze.

**Der Fallstrick: Dieselben Lesemethoden dienen heute zwei Zwecken.**

- **Anzeigen für die Person**, also Listen, Detailseiten, Auswahllisten, Befehlspalette und künftig
  GraphQL. Hier gilt die Zuständigkeit.
- **Auflösen für eine Prüfung oder Berechnung** innerhalb einer Anfrage. Hier darf **nicht** gefiltert
  werden. Beispiele aus dem Code:
  - `BudgetAuthorization` ruft `getAllCustomerorders`.
  - `TimereportVisibilityService` löst die Objekte der Regeln über `getCustomerorderBySign` und
    `getSuborderByCompleteOrderSign` auf, also genau der Baustein, der die Zuständigkeit baut.
  - Budget- und JIRA-Services lösen Aufträge über ihr Zeichen auf.

  Würde dort gefiltert, beißt sich die Prüfung in den Schwanz: Die Regel, die einen Auftrag
  freigibt, würde gar nicht erst gefunden, weil der Auftrag noch nicht freigegeben ist.

**Das Ausmaß:** `CustomerorderService.getCustomerorderBySign` allein wird aus 18 Dateien gerufen,
teils zum Anzeigen, teils zum Auflösen. Den Filter einfach in die bestehenden Methoden einzubauen,
ginge also in beide Richtungen schief: Entweder bleibt eine Anzeige ungefiltert (Lücke), oder eine
Prüfung wird kaputt gefiltert (Fehler).

#### Der Aufrufzweck wird ausdrücklich

Jeder Aufruf einer Lesemethode der Stammdaten muss erkennbar sagen, wozu er liest. Drei Wege dafür:

| Variante | Form | Stärken | Schwächen |
|---|---|---|---|
| **A: `CallPurpose` als Parameter** | `getCustomerorderBySign(sign, CallPurpose.DISPLAY)` bzw. `…RESOLVE` | eine Methode je Abfrage; der Zweck steht an jeder Aufrufstelle; als Pflichtparameter erzwingt der Compiler die Einordnung jedes Aufrufs | Die Umgehung ist ein Wort entfernt: Ein Controller, der `RESOLVE` übergibt, liest ungefiltert. Ein `ArchitectureTest` sieht Klassenabhängigkeiten, aber nicht, welchen Wert ein Argument hat. Der Parameter wandert durch Hilfsmethoden und DAOs mit |
| **B: getrennte Methoden** | `getReadable…` (gefiltert) neben `resolve…` (ungefiltert) im selben Service | Vorbild vorhanden (`EmployeecontractService.getReadableEmployeecontract`); kein Parameter; Benennen der ungefilterten Methoden zwingt jede Aufrufstelle zur Entscheidung | Ob ein Controller eine `resolve…`-Methode ruft, ist nur über Namenskonventionen prüfbar. Die Zahl der Methoden wächst |
| **C: getrennte Methoden in getrennten Bohnen** | anzeigend im Service (`CustomerorderService.getReadable…`), auflösend in einer eigenen Bohne `CustomerorderLookup` desselben Moduls | Die Regel wird zur **Klassenabhängigkeit**, und genau die prüft `ArchitectureTest`: Nichts in `..controller..`, `..graphql..`, `..rest..` und kein `PaletteProvider` hängt von einer `*Lookup`-Klasse ab | eine Bohne mehr je Modul; die auflösenden Methoden ziehen aus den Services um |

**Empfehlung: Variante C**, also getrennte Methoden, aber in getrennten Bohnen.

- **Prüfbar statt vereinbart:** Bei A und B hängt die Sicherheit daran, dass niemand das falsche
  Argument übergibt oder den falschen Namen ruft. Bei C ist der falsche Aufruf eine verbotene
  Abhängigkeit, und der Build schlägt fehl. Das ist dieselbe Art Regel, die `ArchitectureTest` heute
  schon für die Modulgrenzen und gegen `@PreAuthorize` durchsetzt.
- **Der Compiler macht die Erhebung:** Ziehen die auflösenden Methoden in die `Lookup`-Bohne um,
  bricht jede Aufrufstelle, bis sie sich entschieden hat. Die Erhebung erbt damit nicht die blinden
  Flecken einer Suche, denn es gibt keinen Aufruf, der unbemerkt beim Alten bleibt.
- **Gelesen werden darf auch in `Lookup`** nur mit Anmeldung (`@Authorized` an der Klasse). Die Bohne
  ist kein Weg an der Anmeldung vorbei, sondern einer an der Zuständigkeit vorbei, und deshalb nur
  für Services gedacht. Ihr Javadoc sagt das, und jede Methode nennt im Namen, was sie auflöst
  (`bySign`, `byIds`, `all`).
- Die Idee von `CallPurpose` bleibt als **Begriff** erhalten. Die beiden Werte sind die beiden
  Bohnen, nicht ein Parameter.

**Namen:** Die anzeigenden Methoden heißen nach dem, was sie liefern: `getReadable…`. **Nicht**
verwendbar ist das Wort „visible": `getVisibleCustomerorders` heißt heute „nicht verborgen und nicht
inaktiv", also Auswahl für Neues nach ADR-0029, und hat mit Berechtigung nichts zu tun.

Was dafür nötig ist:

- **Die Erhebung aller Aufrufer** der Lesemethoden von `CustomerService`, `CustomerorderService`,
  `SuborderService` und `EmployeeorderService`, jeder Aufruf eingeordnet als *Anzeige* oder
  *Auflösung*. Mit Variante C führt der Compiler sie, sobald die auflösenden Methoden umziehen.
- **Controller, `PaletteProvider` und GraphQL-Resolver rufen nur anzeigende Methoden**, und zwar
  dieselben. Der `ArchitectureTest` verbietet ihnen die Abhängigkeit von `*Lookup`. Ein Test je Typ
  und Rolle vergleicht die Antwort der Liste mit der des Resolvers. Laufen sie auseinander, ist das
  ein Fehler, keine Ausprägung.
- **Ein gespeicherter Wert bleibt bearbeitbar.** Wie bei `hide` (#1005) darf eine Auswahlliste einen
  bereits gespeicherten Bezug nicht verlieren, nur weil er nicht in der Zuständigkeit liegt. Sonst
  schreibt das Speichern stillschweigend einen anderen Wert zurück. Die bestehenden Rückfälle in
  `CustomerorderController.addFormModel` und `EmployeeorderController.addFormModel` sind dafür das
  Vorbild.
- **Management ist nicht betroffen.** Es sieht weiterhin alles, und die Pflegeseiten der Stammdaten
  bleiben unverändert. Spürbar wird die Änderung für Mitarbeitende, People Leads, Backoffice und
  Verantwortliche.

### 5.4 Regeln (`AuthorizationRule`)

Die Regeln wirken über die Services automatisch mit. Eine Regel `xx:1453` der Kategorie
`TIMEREPORT` lässt die Buchungen von xx auf Auftrag 1453 in GraphQL genauso sehen wie in der Liste,
und über die Zuständigkeit (5.2) auch den Auftrag 1453, seinen Kunden und den Mitarbeiterauftrag von
xx darauf. Wer Buchungen auf einem Auftrag sehen darf, sieht auch den Auftrag. Die Schnittstelle führt keine eigene Kategorie ein. Ein späterer Bedarf, etwa „darf die Schnittstelle
überhaupt benutzen", wäre eine neue Kategorie mit eigenem `AuthorizationObjectProvider` und hätte
keine Sonderlogik in der GraphQL-Schicht.

### 5.5 Bewusst ausgenommen

- **Regelpflege** (`AuthorizationRuleService`): Wer Regeln schreibt, kann sich selbst `LOGIN` für
  andere geben. Über eine Maschinenschnittstelle ist das ein Angriffsweg ohne Nutzen.
- **Übernommene Anmeldung:** Die zustandslose Kette kennt sie nicht, und das bleibt so. Ein Skript
  handelt immer als die Person seines Tokens.
- **ETL und Schema-Abgleich:** eigene Oberfläche, eigene Regeln (ADR-0028), kein Bedarf erkennbar.

### 5.6 Schreibende Service-Methoden anpassen (entschieden 01.10.2026)

Die Services werden, wo nötig, so angepasst, dass ihre Berechtigung beim Schreiben **selbst** passt
und dass sie die **id** des angelegten Objekts zurückgeben. Maßstab ist die Regel aus AGENTS.md für
die Service-Ebene: `@Authorized(requires…)` an der Methode **und** ein Laufzeit-Guard im Rumpf, der
eine `AuthorizationException` wirft. Die Annotation allein genügt nicht: Sie greift nur, wenn der
Aufruf durch den Proxy geht. Ein Aufruf aus derselben Klasse umgeht sie.

Erhebung der schreibenden öffentlichen Methoden (Stand 01.10.2026):

| Service | Methoden | Annotation | Guard im Rumpf | Rückgabe beim Anlegen | Anpassung |
|---|---|---|---|---|---|
| `CustomerService` | `createOrUpdate`, `toggleHide`, `deleteCustomerById` | `requiresManager` | ja | `void` | id zurückgeben |
| `CustomerorderService` | `create`, `update`, `toggleHide`, `deleteCustomerorderById` | `requiresManager` | **nein** | Entität | Guard ergänzen, id statt Entität |
| `SuborderService` | `create`, `update`, `toggleHide`, `deleteSuborderById`, `createCopy`, `changeSuborder_customer`, `hideSuborders` | `requiresManager` | **nein** | `void` | Guard ergänzen, id zurückgeben (auch bei `createCopy`) |
| `EmployeeorderService` | `create`, `update`, `deleteEmployeeorderById` | `requiresManager` | **nein** | `void` | Guard ergänzen, Datenobjekt statt Entität, id zurückgeben |
| `EmployeeService` | `createOrUpdate` (zwei Varianten), `toggleHide`, `deleteEmployeeById` | `requiresManager` | ja, aber `createOrUpdate` wirft eine nackte `RuntimeException` | `void` | `AuthorizationException` statt `RuntimeException`, Datenobjekt statt Entität, id zurückgeben, `SalatUser` im Service anlegen (2.6) |
| `EmployeecontractService` | `createEmployeecontract`, `updateEmployeecontract` | `requiresManager` | ja | `ContractStoredInfo` mit id | keine |
| `EmployeecontractService` | `toggleHide`, `deleteEmployeeContractById` | `requiresManager` | **nein** | — | Guard ergänzen |
| `EmployeecontractService` | `create(Overtime)` (Überstundenkorrektur, gerufen von `EmployeecontractController`) | **keine** | **nein** | `void` | Annotation und Guard ergänzen, id zurückgeben (#1256) |
| `TimereportService` | `createTimereports`, `updateTimereport`, `delete…` | Klasse | ja, über `TimereportAuthorization` in Hilfsmethoden | `void` | ids der angelegten Buchungen zurückgeben (eine Anlage kann mehrere Tage erzeugen) |
| `WorkingdayService` | `upsertWorkingday`, `markNotWorked`, `deleteWorkingdayById` | Klasse | ja, über `writeDenial` | `void` | id zurückgeben |
| `ReleaseService` | `releaseTimereports`, `acceptTimereports` | Klasse | ja | — | keine |
| `ReleaseService` | `reopenTimereports` | `requiresAdmin` | **nein** | — | Guard ergänzen |

**Öffentlich, aber nur für den internen Gebrauch.** Einige schreibende Methoden sind `public`,
werden aber nur von anderen Services gerufen und prüfen nichts:
`TimereportService.updateReleaseData`, `ReleaseService.reopenTimereport`,
`EmployeecontractService.updateOvertimeStatic` und `updateReportReleaseData`. Sie setzen
Freigabestatus und Überstundenstand direkt. Über einen Resolver erreichbar wären sie eine Hintertür
an der Statusregel vorbei. Sie bekommen deshalb keine Mutation. Wo es die Paketstruktur erlaubt,
werden sie enger sichtbar gemacht. Sonst bekommen sie einen Javadoc-Vermerk, dass sie nur aus dem
Service-Fluss gerufen werden. Der `ArchitectureTest` aus 5.3 lässt Controller und Resolver nur die
dafür vorgesehenen Methoden rufen.

**Was die id-Rückgabe sonst noch bringt:** Die Oberfläche kann nach dem Anlegen direkt auf das neue
Objekt weiterleiten, statt es über Zeichen oder Liste wiederzufinden. Der Idempotenz-Baustein (6.5)
hat die id, die er sich zum Schlüssel merkt.

Die Anpassungen gehören zu Stufe 3b (→ 8), je Geschäftsobjekt zusammen mit dem Umzug der Prüfungen
aus dem Controller. Wo eine fehlende Prüfung eine echte Lücke ist (Überstundenkorrektur ohne
Annotation und Guard), wird nicht auf GraphQL gewartet: #1256.

---

## 6. Querschnittsthemen

### 6.1 Endpunkt und Authentifizierung

- `POST /api/graphql` in der bestehenden JWT-Kette. Kein zweiter Authentifizierungsweg.
- **Introspection für angemeldete Aufrufer, auch in Produktion** (entschieden 01.10.2026). Werkzeuge
  und KI-Agenten erschließen sich das Schema darüber selbst. Ohne gültiges Token gibt es weder
  Abfrage noch Introspection, denn der Endpunkt liegt vollständig in der JWT-Kette. Das Schema
  zeigt allen Angemeldeten dieselben Typen und Felder. Was jemand **sehen** darf, entscheiden die
  Daten, nicht das Schema. Ein Feldname ist kein Geheimnis, und ein je Rolle beschnittenes Schema
  wäre eine zweite Ausprägung der Regel.
- Die Introspection unterliegt denselben Grenzwerten wie jede Abfrage (6.2). Tief verschachtelte
  Introspection-Abfragen sind ein bekannter Weg zu teuren Anfragen.
- Die GraphiQL-Oberfläche gibt es nur in `local` und `local-qa`. Sie bräuchte eine
  Browser-Sitzung, und die gibt es unter `/api` nicht (nächster Punkt). Zusätzlich wird das Schema
  als Datei veröffentlicht, wie die OpenAPI-Beschreibung unter `/api/doc`.
- Kein Browser-Sitzungszugang. Für Endpunkte unter `/api` gilt AGENTS.md: Diese Pfade sind
  zustandslose Ketten für Maschinen.

### 6.2 Schutz vor teuren Abfragen

Ein Graph macht die teure Abfrage leicht. Pflicht von Anfang an:

- maximale **Tiefe** (Vorschlag: 8) und **Komplexität** je Dokument;
- **feste Obergrenze der Seitengröße** je Liste (z. B. 200);
- **Zeitlimit** je Ausführung;
- **DataLoader / Batch-Laden** gegen N+1, und zwar über Service-Methoden, die selbst filtern
  (`get…ByIds` mit Sichtbarkeit). Ein Batch-Loader, der direkt ein Repository fragt, umgeht
  Ebene 2. Das ist der wahrscheinlichste Weg, auf dem die Schnittstelle unbemerkt zu viel zeigt.
- **Mengenbegrenzung je Aufrufer** (Anfragen je Minute, je Person des Tokens). KI-Agenten und
  fehlerhafte Skripte stellen Anfragen in Schleifen. Eine Grenze je Dokument schützt nicht vor
  tausend günstigen Dokumenten.

### 6.3 Fehlerabbildung

`ErrorCodeException` wird zu einem GraphQL-Fehler mit fachlichem Code und übersetzter Meldung
(Sprache aus `Accept-Language`, deutsch als Vorgabe):

```json
{
  "errors": [{
    "message": "Eigene Buchungen können nicht vor dem Freigabedatum erzeugt oder geändert werden.",
    "path": ["updateTimereport"],
    "extensions": { "code": "TR-0026", "classification": "FORBIDDEN" }
  }],
  "data": { "updateTimereport": null }
}
```

| Ausnahme | `classification` |
|---|---|
| `AuthorizationException` `AA-0001` | `UNAUTHORIZED` |
| übrige `AuthorizationException` | `FORBIDDEN` |
| `InvalidDataException` | `BAD_REQUEST` (nicht gefunden: `NOT_FOUND`) |
| `BusinessRuleException`, `VetoedException` | `BAD_REQUEST`, Veto-Meldungen einzeln |
| `XX-0003` (Versionskonflikt) | `CONFLICT` |
| `TechnicalException`, alles andere | `INTERNAL_ERROR`, ohne Details |

Die Übersetzung liegt an **einer** Stelle, einem Exception-Resolver der GraphQL-Schicht, neben
`AuthorizationExceptionHandler`, und nutzt dieselben `errorcode.*`-Schlüssel wie
`ErrorCodeViewHelper`. Fehlt der Resolver, liefert die Engine eine allgemeine Fehlermeldung, und
der fachliche Grund ist verloren.

Bezieht sich eine Meldung auf ein Eingabefeld, steht sein Name zusätzlich in `extensions.field`
(z. B. `"field": "sign"` bei einem schon vergebenen Auftragszeichen). Es ist derselbe Feldbezug,
den die Oberfläche zum Markieren des Feldes nutzt (3.5).

**Nicht gefunden und nicht sichtbar sehen gleich aus.** `customerorder(id: 4711)` liefert `null`,
wenn es den Auftrag nicht gibt **oder** wenn die Person ihn nicht sehen darf. Sonst ließe sich durch
Durchprobieren der ids feststellen, was existiert.

### 6.4 Laufzeitkontext (Request-Scope, Open-in-View)

Der heikelste technische Punkt (2.5). Bevor eine Alternative gewählt wird, muss ein Spike
nachweisen:

1. `AuthorizedUser` und `SecurityContext` sind in jedem Resolver verfügbar, auch in Batch-Loadern
   und nach asynchronen Schritten (Kontextweitergabe an Worker-Threads, oder Ausführung bewusst im
   Thread der Anfrage).
2. Lazy-Assoziationen werden nicht außerhalb des Request-Threads nachgeladen. Besser noch: Die
   Resolver arbeiten nur auf DTOs (3.2), dann stellt sich die Frage nicht.
3. `ConcurrentModificationAspect` und `AuthorizationAspect` greifen beim Aufruf über die
   GraphQL-Schicht genauso wie über einen Controller.

### 6.5 Doppeltes Anlegen: Idempotenzschlüssel für alle Geschäftsobjekte

In der Oberfläche schützt `data-submit-once` vor doppelt angelegten Buchungen. Ein Aufrufer, der
nach einem Zeitlimit wiederholt, legt doppelt an, obwohl der erste Versuch durchgegangen ist und nur
die Antwort verloren ging. Entschieden (01.10.2026): **Jede anlegende Mutation, für jedes
Geschäftsobjekt, verlangt einen Idempotenzschlüssel.**

- **Pflicht, nicht optional.** `idempotencyKey: ID!` ist bei `create…` und `copy…` ein Pflichtfeld.
  Ein optionaler Schlüssel wird genau von den Skripten vergessen, die ihn bräuchten. Der Aufrufer
  erzeugt ihn, typischerweise als UUID, einmal je fachlichem Vorhaben, und schickt ihn bei jeder
  Wiederholung unverändert mit.
- **Wirkung:**
  - Ein erster Aufruf legt an und merkt sich Schlüssel und id.
  - Eine Wiederholung mit **demselben Schlüssel und derselben Eingabe** legt nichts an und
    antwortet mit dem damals angelegten Objekt in seinem heutigen Stand.
  - Derselbe Schlüssel mit **anderer Eingabe** ist ein Fehler des Aufrufers und wird mit einem
    eigenen Fehlercode abgewiesen (`CONFLICT`). Er wird nicht stillschweigend als Wiederholung
    behandelt.
- **Gültigkeitsbereich:** Ein Schlüssel gilt je Person des Tokens. Zwei Aufrufer können denselben
  Schlüssel nicht gegenseitig verbrauchen, und eine Antwort verrät nie, was jemand anderes angelegt
  hat.
- **Ablage:** eine eigene Tabelle (Bewegungsdaten) mit Person, Schlüssel, Operation, Prüfsumme der
  Eingabe, id des Ergebnisses und Zeitpunkt, eindeutig über Person und Schlüssel. Ein
  Aufräumjob löscht Einträge nach einer festen Frist, z. B. 7 Tage, wie es
  `NotificationCleanupService` und `ETLExecutionHistoryCleanupService` für ihre Tabellen tun.
- **In derselben Transaktion wie das Anlegen.** Schlüssel und Objekt werden gemeinsam geschrieben.
  Scheitert das Anlegen, gibt es auch keinen Schlüssel, und die Wiederholung darf es erneut
  versuchen. Kommen zwei Aufrufe mit demselben Schlüssel gleichzeitig, scheitert der zweite an der
  Eindeutigkeit und bekommt das Ergebnis des ersten.
- **Ort:** ein gemeinsamer Baustein in `common`, den jeder anlegende Resolver um seinen
  Service-Aufruf legt. Er kennt kein Fachmodul, nur die Operation, die Eingabe und die
  zurückgegebene id. Deshalb müssen die anlegenden Service-Methoden die id liefern (2.6).
- **Andere Mutationen:** Änderungen sind über `version` schon geschützt. Die Wiederholung einer
  durchgegangenen Änderung meldet allerdings `XX-0003`, obwohl alles geklappt hat. Der Schlüssel ist
  deshalb bei allen übrigen Mutationen (`update…`, `delete…`, Freigabe, Abnahme) **optional**
  möglich, mit derselben Wirkung.

**Davon zu trennen ist die fachliche Dopplung.** Zwei *verschiedene* Vorhaben, die dasselbe
anlegen, also zweimal derselbe Auftrag oder zwei überlappende Mitarbeiteraufträge, verhindert nicht
der Schlüssel, sondern die Prüfung im Service: eindeutiges Zeichen, keine Überlappung, kein
doppelter Name (2.6, 3.5). Der Schlüssel fängt nur die technische Wiederholung ab.

### 6.6 Protokoll und Beobachtung

- Jede Mutation wird mit Kürzel, Operation und betroffener id geloggt, wie es
  `AuthorizationRuleService` vormacht. Das Kürzel im Log ist datenschutzseitig geklärt.
- Der Operationsname (`operationName`) kommt in Metriken und Log, damit der Produktionsbericht
  GraphQL-Nutzung je Operation sehen kann, so wie heute die REST-Endpunkte.

### 6.7 Tests

- **Berechtigungen:** Unit-Tests an Service und `*Authorization`, keine E2E-Tests. Neu kommt dazu,
  dass für jeden Typ ein Test die Antwort für jede Rolle festhält: Management, People Lead,
  Backoffice, Mitarbeitende, Eingeschränkte.
- **Schema:** Ein Test schlägt fehl, wenn ein Feld keinen Resolver hat oder ein Resolver kein
  `@Authorized` trägt (3.4).
- **Durchstich:** Integrationstests über HTTP gegen das Schema je Stufe.
- **Konsistenz:** Für Buchungen gibt es den Test, der `TimereportVisibility.covers` gegen
  `isAuthorized` hält. Jede neue Listen-Sichtbarkeit bekommt das Gleiche.

---

## 7. Technische Umsetzung: drei Alternativen

Alle drei setzen auf **graphql-java** als Ausführungsmaschine. Sie unterscheiden sich im
Programmiermodell, in der Integration mit Spring und in ihrer Reife. Der frühere Platzhirsch
`graphql-java-kickstart` ist archiviert; seine Maintainer verweisen auf Spring for GraphQL.

### 7.1 Alternative 1: Spring for GraphQL (der Spring-Standard)

**Was es ist.** Das offizielle Spring-Projekt, im BOM von Spring Boot enthalten (Boot 4.1 bringt
Spring for GraphQL 2.0 mit graphql-java 25). Starter `spring-boot-starter-graphql`, keine eigene
Versionsverwaltung.

**Programmiermodell.** Schema-first: Die Schema-Dateien liegen unter `resources/graphql/**` und
werden beim Start zusammengefügt. Die Resolver sind annotierte Methoden in Spring-`@Controller`-Klassen
(`@QueryMapping`, `@MutationMapping`, `@SchemaMapping` für Felder, `@BatchMapping` für Batch-Laden).
Beim Start prüft eine Schema-Inspektion und meldet Felder ohne Resolver und Resolver ohne Feld.

**Passung zu Salat.**

- Resolver sind gewöhnliche Spring-Bohnen. `@Authorized` und `AuthorizationAspect` wirken ohne
  Anpassung, also dasselbe Muster wie bei den Controllern.
- Das Schema lässt sich je Modul ablegen (`resources/graphql/<modul>.graphqls`). Typerweiterungen
  (3.7) werden unterstützt.
- `@ControllerAdvice`-ähnliche Fehlerbehandlung (`@GraphQlExceptionHandler`,
  `DataFetcherExceptionResolver`) für 6.3.
- Spring Security, Observation/Micrometer, `GraphQlTester` für Tests, GraphiQL eingebaut.
- Kontextweitergabe über Spring-Mechanismen (`context-propagation`, `ThreadLocalAccessor`). Ob das
  für die Request-Scope-Bohne reicht, ist genau die Frage des Spikes (6.4).
- Grenzwerte (6.2) über graphql-java-Instrumentierungen, als Bohnen registriert.

**Stärken.** Kein Fremdframework, Versionen kommen mit Boot, die Dokumentation und das
Programmiermodell sind dem Team vertraut, und das Projekt ist langfristig gepflegt.

**Schwächen.** Keine eingebaute Codegenerierung für Java-Typen aus dem Schema. Die Typen werden
von Hand geschrieben oder mit einem separaten Generator erzeugt. Federation gibt es nur über
eine Zusatzbibliothek. Beides braucht Salat nicht.

**Aufwand.** Gering für die Infrastruktur. Der Aufwand steckt in Schema, Resolvern und den
Service-Lücken, und der ist bei allen drei Alternativen gleich.

### 7.2 Alternative 2: Netflix DGS Framework (der verbreitetste Java-Ansatz)

**Was es ist.** Das GraphQL-Framework von Netflix, seit 2021 Open Source und das am weitesten
verbreitete eigenständige GraphQL-Framework im Java-Umfeld. DGS 11 und folgende laufen auf
Spring Boot 4; die 12er-Reihe bringt Jackson 3, passend zu Salat. **Seit DGS 8.5 nutzt DGS intern
Spring for GraphQL** für Transport und Ausführung. Es ist damit eine Schicht über Alternative 1,
nicht daneben.

**Programmiermodell.** Ebenfalls schema-first. Die Resolver sind `@DgsComponent`-Klassen mit
`@DgsQuery`, `@DgsMutation` und `@DgsData`, dazu `@DgsDataLoader`. Kern des Angebots ist die
**Codegenerierung**: Aus dem Schema entstehen Java-Typen, Konstanten für Feldnamen und ein
typisierter Client für Tests. Federation ist eingebaut.

**Passung zu Salat.**

- `@DgsComponent` sind Spring-Bohnen, also wirkt `@Authorized` auch hier.
- Die Codegenerierung ist **Gradle-first**. Für Maven gibt es nur ein Community-Plugin. Salat baut
  mit Maven; das ist eine Abhängigkeit von einem Drittprojekt im Build.
- Netflix rät davon ab, DGS- und Spring-GraphQL-Resolver zu mischen. Man legt sich also auf das
  DGS-Modell fest.
- Die Kontextfrage (6.4) stellt sich wie bei Alternative 1. Netflix hat die asynchrone Ausführung
  der Spring-Ebene standardmäßig abgeschaltet, was für Request-Scope und Open-in-View eher günstig ist.

**Stärken.** Bei Netflix im großen Maßstab erprobt, Codegenerierung spart Tipparbeit und hält
Schema und Java-Typen deckungsgleich, guter Testclient, große Community.

**Schwächen.** Eine zusätzliche Abstraktion über dem Spring-Standard, deren Mehrwert heute vor
allem Codegenerierung und Federation ist. Ein Föderationsbedarf besteht bei einem einzelnen
Monolithen nicht. Versionssprünge laufen hinter Spring for GraphQL her: Das Upgrade auf Spring
for GraphQL 2.1 ist dort gerade erst als Issue geplant.

**Aufwand.** Gering bis mittel, zusätzlich das Maven-Codegen-Plugin.

### 7.3 Alternative 3: Viaduct von Airbnb (der neue Stern am Himmel)

**Was es ist.** Die GraphQL-Plattform, auf der Airbnbs zentrale Datenschicht läuft. Open Source
seit September 2025, **Version 1.0 im Mai 2026** mit stabiler öffentlicher API, Veröffentlichung
auf Maven Central und erstmals einer vollständigen **Java-API** (Annotationen,
Resolver-Basisklassen, Java-Codegenerator). Ursprünglich Kotlin.

**Programmiermodell.** Das Leitkonzept sind **Tenant Modules**: Eine Einheit aus Schema und den
Resolvern, die es umsetzen, im Besitz genau eines Teams. Module hängen nicht über Code voneinander
ab, sondern setzen sich über GraphQL-Fragmente und -Abfragen zusammen. Viaduct bildet daraus *einen*
Graphen; es braucht weder Router noch Komposition. Für Berechtigungen gibt es eigene
„Checker", die getrennt von den Resolvern laufen.

**Passung zu Salat.**

- **Konzeptionell die beste Passung.** Tenant Modules sind die Module des modularen Monolithen,
  und „keine Code-Abhängigkeit zwischen Modulen, Zusammensetzung über den Graphen" ist ADR-0003
  und ADR-0021 in GraphQL-Form. Eine Kante `Suborder.timereports` stünde im Modul `dailyreport`,
  ohne dass `order` etwas davon weiß, und das vom Framework so vorgesehen.
- Checker sind ein naheliegender Ort für Ebene 1. Wie gut sie mit `@Authorized` und der
  Request-Scope-Bohne zusammengehen, ist unbekannt.
- Spring-Integration gibt es als Demo-Anwendung, nicht als Starter.
- **Build:** Codegenerierung und Schemaprüfung laufen über ein **Gradle-Plugin**. Für Maven ist
  nichts bekannt. Das ist für Salat die größte praktische Hürde.
- Die Java-API ist neu. Nach dem Stand der Recherche liegt ein Teil davon im Repository noch im
  experimentellen Bereich. Kotlin kommt als Laufzeitabhängigkeit mit.

**Stärken.** Modulgrenzen sind Teil des Programmiermodells statt einer Konvention, die
`ArchitectureTest` nachträglich prüft. Bei Airbnb im großen Maßstab erprobt, aktive Entwicklung,
eingebaute Relay-Paginierung und Schemaprüfung zur Build-Zeit.

**Schwächen.** Junge Open-Source-Gemeinschaft, wenig Erfahrung außerhalb von Airbnb, Gradle und
Kotlin im Gepäck, unklare Reife der Java-API und der Checker. Ausgelegt für Hunderte von Teams und
Tausende Typen. Für die Größe von Salat ist vieles davon Ballast.

**Aufwand.** Hoch: Build-Integration, Einarbeitung, eigener Klebstoff zu Spring Security und
`AuthorizedUser`. Die Angaben zu Viaduct stammen aus einer Recherche im Oktober 2026 und müssen vor
einer Entscheidung im Spike bestätigt werden.

### 7.4 Vergleich

| Kriterium | 1 Spring for GraphQL | 2 Netflix DGS | 3 Viaduct |
|---|---|---|---|
| Teil des Boot-BOM | ja | nein (eigene BOM) | nein |
| Maven-Build | nativ | Codegen nur über Community-Plugin | Gradle-Plugin, Maven offen |
| `@Authorized`/AOP wirkt unverändert | ja | ja | ungeklärt (Checker) |
| Modulgrenzen | Konvention + `ArchitectureTest` | Konvention + `ArchitectureTest` | im Modell eingebaut |
| Codegenerierung | nein (optional extern) | ja, Kern des Angebots | ja |
| Reife außerhalb des Herstellers | hoch | hoch | gering |
| Abhängigkeitsrisiko | gering | mittel (läuft Spring hinterher) | hoch |
| Einstiegsaufwand | gering | gering bis mittel | hoch |

### 7.5 Empfehlung und Wahl

**Gewählt (01.10.2026, vorbehaltlich der Bestätigung des Konzepts): Alternative 1, Spring for GraphQL.** Sie bringt alles mit, was das Konzept verlangt: Schema je
Modul, Typerweiterungen, Batch-Laden, Fehlerabbildung, Spring Security, und das ohne neue
Abhängigkeit außerhalb des Boot-BOM. `@Authorized` wirkt ohne Umbau. DGS legt eine Schicht darüber,
deren Kernnutzen (Codegenerierung, Federation) Salat kaum braucht, und kostet dafür ein Maven-Plugin
aus der Community. Viaduct ist die interessanteste Idee, vor allem für die Modulgrenzen, aber heute
zu jung, zu Gradle-gebunden und zu groß für einen einzelnen Monolithen. Es lohnt sich, das in einem
Jahr wieder anzusehen.

Die Grundsätze aus 3 bis 6 hängen nicht an der Wahl. Ein späterer Wechsel betrifft die
Resolver-Schicht, nicht das Schema und nicht die Services.

---

## 8. Vorgehen in Stufen

| Stufe | Inhalt | Voraussetzung |
|---|---|---|
| 0 Spike | Endpunkt, ein Lesefeld (`me`), Nachweis 6.4 (Request-Scope, Open-in-View, Aspekte), Fehlerabbildung, Grenzwerte, Introspection, Baustein für Idempotenz (6.5) | Konzept als Ganzes bestätigt, neues ADR |
| 1 Eigene Daten lesen | `me`, `bookableSuborders`, eigene Buchungen und Arbeitstage. Deckt ab, was `EmployeeOrderRestEndpoint` (seit #1253 der einzige für Mitarbeiteraufträge), `DailyReportRestEndpoint` und `WorkingDayRestEndpoint` heute bieten | Stufe 0 |
| 2 Buchen | Buchung anlegen, ändern und löschen, Arbeitstag, „Nicht gearbeitet" | Stufe 1 |
| 3a Zuständigkeit (eigenes Vorhaben, ohne GraphQL) | Baustein nach `order`, auflösende Lesemethoden in `*Lookup`-Bohnen, anzeigende Lesemethoden filtern, `ArchitectureTest` gegen `*Lookup` aus Controllern, Oberfläche zieht mit (5.3), Konsistenztests, Messung | — |
| 3b Prüfungen in die Services (eigenes Vorhaben je Geschäftsobjekt, ohne GraphQL) | fachliche Prüfungen aus den Controllern in die Services (2.6), Berechtigung beim Schreiben mit Guard, anlegende Methoden nehmen Datenobjekte und liefern die id (5.6) | Zuordnung der Prüfungen je Controller (3.5), Feldbezug in `ServiceFeedbackMessage` |
| 3c Stammdaten über GraphQL | Kunden, Aufträge, Unteraufträge, Mitarbeiteraufträge, Personen und Verträge lesen (nach Zuständigkeit bzw. eigener Regel) und anlegen, ändern, ausblenden, löschen (Management) | Stufen 3a und 3b für das jeweilige Objekt |
| 4 Freigabe und Abnahme | `releaseReview`, `releaseTimereports`, `acceptTimereports`, `reopenTimereports` | Stufe 2 |
| 5 später | Budget, Controlling, Berichte, Rechnungen | eigener Bedarf |

Die REST-Endpunkte bleiben bestehen. Ob sie später abgelöst werden, entscheidet die Nutzung, die
der Produktionsbericht je Endpunkt zeigt.

---

## 9. Offene Fragen und Entscheidungen

Offen ist keine Einzelfrage mehr. Offen ist die Bestätigung des Konzepts als Ganzes. Erst danach
entsteht das neue ADR.

Entschieden am 01.10.2026:

- Die Oberfläche zieht bei der Zuständigkeit mit (5.3).
- Vertragsverantwortliche zählen wie Durchführungsverantwortliche (5.2).
- Der Baustein der Zuständigkeit zieht nach `order` um (5.2).
- Aufrufer sind potenziell alle: Skripte, Werkzeuge, mobile Oberfläche, Integrationen, KI-Agenten (1).
- Die Schnittstelle legt alle Geschäftsobjekte an, nicht nur Buchungen (1, 3.5).
- Jede anlegende Mutation verlangt einen Idempotenzschlüssel (6.5).
- Introspection ist auch in Produktion für angemeldete Aufrufer an (6.1).
- Eingeschränkte Anmeldungen dürfen die Schnittstelle nutzen, im Rahmen ihrer Zuständigkeit (5.2).
- Feldnamen sind englisch, Beschreibungen deutsch (4).
- Es gibt keinen versionierten Pfad; das Schema wächst nur durch Hinzufügen und `@deprecated` (4).
- Die Services werden angepasst, damit ihre Berechtigung beim Schreiben selbst passt und sie ids
  zurückgeben (5.6).
- Jeder Aufruf geschieht im Namen einer Person, auch bei Integrationen. Technische Konten gibt es
  nicht (1).
- Technische Umsetzung mit Spring for GraphQL (7.5).
- Die fehlende Berechtigungsprüfung bei der Überstundenkorrektur ist als #1256 erfasst (5.6).
- Fachliche Regeln gehören in den Service; am Formular bleiben nur Formatprüfungen (3.5).
- Der Aufrufzweck (Anzeige oder Auflösung) wird über getrennte Bohnen ausdrücklich: anzeigend im
  Service, auflösend in `*Lookup` (5.3, Variante C).
