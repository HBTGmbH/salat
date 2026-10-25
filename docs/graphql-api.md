# GraphQL-Schnittstelle

Salat bietet unter `/api/graphql` eine GraphQL-Schnittstelle für Skripte, Werkzeuge, Integrationen und
KI-Agenten (#1532, ADR-0044). Jeder Aufruf geschieht im Namen der angemeldeten Person und darf genau
das, was diese Person auch in der Oberfläche darf.

Version 1 umfasst:
- die eigene Person und worauf sie buchen darf,
- die eigenen Buchungen und die Buchungsliste nach denselben Sichtbarkeitsregeln wie die Seite,
- Buchungen anlegen, ändern und löschen,
- den eigenen Arbeitstag lesen und pflegen.

Stammdaten, Freigabe und Abnahme kommen später (Konzept [`ideen/graphql-konzept.md`](ideen/graphql-konzept.md), Abschnitt 8).

## Das Schema ist die Referenz

Was es gibt und was es bedeutet, steht im Schema selbst. Jeder Typ, jedes Feld und jedes Argument hat
eine deutsche Beschreibung.

- **Introspection:** Werkzeuge wie GraphiQL, Postman, Insomnia oder IDE-Plugins lesen das Schema selbst
  und bieten Autovervollständigung.
- **Als Datei:** `GET /api/graphql/schema` liefert das Schema der laufenden Anwendung. Der
  veröffentlichte Stand liegt in [`graphql-schema.graphqls`](graphql-schema.graphqls).
- **Ohne Versionspfad:** Das Schema wächst nur durch Hinzufügen. Ein Feld, das wegfallen soll, bleibt
  mit `@deprecated` und dem Hinweis auf seinen Ersatz erhalten, bis es niemand mehr nutzt.

## Anmelden

- **In einer Umgebung:** Die Schnittstelle liegt in derselben zustandslosen Kette wie die
  REST-API unter `/api`. Sie erwartet dasselbe Bearer-Token wie die REST-API, ausgestellt für die
  Salat-API: `Authorization: Bearer <token>`. Wie man an ein Token kommt, zeigt die Swagger-UI der
  REST-API unter `/api/doc` über „Authorize". Ein Token aus einer reinen Anwendungsanmeldung ohne
  Person wird abgewiesen.
- **Lokal:** Das Kürzel steht als Parameter `login-name` in der URL, zum Beispiel
  `POST http://localhost:8080/api/graphql?login-name=<kürzel>`. GraphiQL gibt es nur lokal, unter
  `http://localhost:8080/graphiql?login-name=<kürzel>&path=/api/graphql%3Flogin-name%3D<kürzel>`.

## Grenzwerte

| Was | Grenze | Antwort darüber |
|---|---|---|
| Anfragen je Login | 120 je Minute | HTTP 429 mit `Retry-After: 60` |
| Tiefe einer fachlichen Abfrage | 8 | Fehler, keine Daten |
| Komplexität einer fachlichen Abfrage | 200 Felder | Fehler, keine Daten |
| Introspection | begrenzt durch graphql-java („good faith"): jedes Feld höchstens einmal, keine endlose Tiefe | Fehler |
| Laufzeit einer Anfrage | 10 Sekunden | Fehler |
| Zeitraum (`timereports`, `timereportList`) | 366 Tage | `GQ-0001` |
| Zeilen je Seite (`timereportList`) | 200 | `GQ-0002` |

Für die Summen ist die Zeilengrenze egal: `totalCount` und `totalDuration` zählen alle Treffer.

## Fehler

GraphQL antwortet mit HTTP 200 und meldet Fehler im Feld `errors`. Ein Fehler trägt in `extensions`
den fachlichen Code und eine Einordnung. Die Meldung ist in der Sprache von `Accept-Language`
(Deutsch ohne Angabe), mit denselben Texten wie die Oberfläche.

```json
{
  "errors": [{
    "message": "Die Daten wurden gleichzeitig an anderer Stelle geändert, etwa durch einen doppelten Klick. Bitte prüfe den aktuellen Stand.",
    "path": ["updateTimereport"],
    "extensions": { "code": "XX-0003", "codes": ["XX-0003"], "classification": "CONFLICT" }
  }],
  "data": null
}
```

| `classification` | Bedeutung |
|---|---|
| `UNAUTHORIZED` | nicht angemeldet (`AA-0001`) |
| `FORBIDDEN` | angemeldet, aber nicht berechtigt (`AA-…`, Statusregeln) |
| `NOT_FOUND` | nicht gefunden **oder nicht sichtbar**; beides sieht gleich aus |
| `CONFLICT` | die Version passt nicht (`XX-0003`) oder der Idempotenzschlüssel wurde mit anderer Eingabe verwendet (`XX-0006`) |
| `BAD_REQUEST` | eine fachliche Regel ist verletzt, z. B. kein Arbeitsbeginn am Tag (`TR-0024`) |
| `INTERNAL_ERROR` | ein unerwarteter Fehler, ohne Details |

## Ändern: die gelesene Version mitschicken

Jede Buchung trägt eine `version`. `updateTimereport` verlangt die Version, in der der Aufrufer die
Buchung gelesen hat. Hat sie seitdem jemand anderes geändert, in der Oberfläche oder über ein anderes
Skript, kommt `XX-0003` / `CONFLICT` statt eines stillen Überschreibens. Dann neu lesen und die
Änderung erneut anwenden.

## Anlegen: ein Idempotenzschlüssel je Vorhaben

`createTimereport` verlangt einen `idempotencyKey`, zum Beispiel eine UUID. Der Aufrufer erzeugt ihn
einmal je fachlichem Vorhaben und schickt ihn bei jeder Wiederholung unverändert mit.

- **Erster Aufruf:** legt an und antwortet mit `replayed: false`.
- **Wiederholung mit demselben Schlüssel und derselben Eingabe**, etwa nach einem Zeitlimit: legt
  nichts an und antwortet mit der damals angelegten Buchung und `replayed: true`.
- **Derselbe Schlüssel mit anderer Eingabe:** wird mit `XX-0006` / `CONFLICT` abgewiesen.

Ein Schlüssel gilt je Person und wird nach sieben Tagen vergessen.

## Beispiele

Wer bin ich, und worauf darf ich heute buchen?

```graphql
{
  me {
    employeeId sign currentContractId
    bookableSuborders(on: "2026-10-26") { suborderId completeOrderSign description commentNecessary }
  }
}
```

Einen Tag buchen: erst der Arbeitsbeginn, dann die Buchung.

```graphql
mutation {
  upsertWorkingday(input: { date: "2026-10-26", startTime: "08:30", breakLength: "PT30M" }) { date startTime }
}
```

```graphql
mutation($key: ID!) {
  createTimereport(idempotencyKey: $key, input: {
    suborderId: 4711, date: "2026-10-26", duration: "PT2H", taskDescription: "Wartung" }) {
    replayed
    timereport { id version duration suborder { completeOrderSign } }
  }
}
```

Eine Buchung ändern, mit der gelesenen Version:

```graphql
mutation {
  updateTimereport(id: 123, version: 0, input: { duration: "PT2H30M" }) { timereport { duration version } }
}
```

Die Buchungsliste, wie die Seite sie zeigt. Personen werden über ihre id angesprochen, nicht über
das Kürzel:

```graphql
{
  timereportList(filter: { from: "2026-10-01", until: "2026-10-31", employeeIds: [42], limit: 50 }) {
    totalCount totalDuration truncated
    items { date duration employee { sign } suborder { completeOrderSign } taskDescription }
  }
}
```
