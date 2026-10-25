# GraphQL-Schnittstelle

Salat bietet unter `/api/graphql` eine GraphQL-Schnittstelle für Skripte, Werkzeuge, Integrationen und
KI-Agenten (#1532, ADR-0044). Jeder Aufruf geschieht im Namen der angemeldeten Person und darf genau
das, was diese Person auch in der Oberfläche darf.

Version 1 umfasst:
- die eigene Person und worauf sie buchen darf,
- die eigenen Buchungen und die Buchungsliste nach denselben Sichtbarkeitsregeln wie die Seite,
- Buchungen anlegen, ändern und löschen,
- den eigenen Arbeitstag lesen und pflegen,
- die Historie, in welchen Zeiträumen eine Person auf welchem Kundenauftrag gearbeitet hat, mit Auftrag,
  Kunde und Kundensegment (#1534).

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

- **Im Browser:** Wer in Salat angemeldet ist, öffnet GraphiQL unter `/graphiql`. Der Link steht im
  Footer neben „API". GraphiQL nutzt die Sitzung des Browsers, Abfragen und Mutationen laufen also
  im Namen der angemeldeten Person. Ebenso lässt sich `/api/graphql/schema` direkt im Browser
  öffnen.
  - Die Seite lädt React und GraphiQL von esm.sh, einem öffentlichen CDN. Das Restrisiko ist in
    ADR-0044 festgehalten.
- **Übernommene Anmeldung:** Unter `/api` und in GraphiQL gilt immer das eigene Login. Wer in der
  Oberfläche gerade als jemand anderes unterwegs ist, liest und bucht in GraphiQL als er selbst.
- **Skripte, Werkzeuge, Agenten:** Sie schicken ein Bearer-Token für die Salat-API mit,
  `Authorization: Bearer <token>`. Es ist dasselbe Token wie für die REST-API. Ein Token aus einer
  reinen Anwendungsanmeldung ohne Person wird abgewiesen. Ohne Browser bekommt man es über den
  Device-Code-Flow von Entra ID. Mandant, Client-ID und Scope sind Platzhalter, die Werte nennt das
  Salat-Team:

  ```
  # 1. Code anfordern; die Antwort nennt eine URL und einen Code für den Browser
  curl -s https://login.microsoftonline.com/<tenant>/oauth2/v2.0/devicecode \
    -d client_id=<client-id> -d scope="<api-scope> offline_access"

  # 2. Nach der Anmeldung im Browser das Token abholen
  curl -s https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token \
    -d grant_type=urn:ietf:params:oauth:grant-type:device_code \
    -d client_id=<client-id> -d device_code=<device_code aus Schritt 1>

  # 3. Aufrufen
  curl -s <salat-url>/api/graphql -H "Authorization: Bearer <access_token>" \
    -H "Content-Type: application/json" -d '{"query":"{ me { sign } }"}'
  ```
- **Nur `POST` mit `application/json`:** Andere Formate beantwortet der Endpunkt mit 415. Das ist der
  Schutz gegen Anfragen fremder Seiten mit der Sitzung des Browsers.
- **Lokal:** Nach der Entwicklungsanmeldung (`?login-name=<kürzel>`) öffnet der Footer-Link GraphiQL
  mit diesem Login. Wie in den Umgebungen trägt die Sitzung auch unter `/api`, hier über das Cookie
  der Entwicklungsanmeldung. Skripte nennen das Kürzel als Parameter, zum Beispiel
  `POST http://localhost:8080/api/graphql?login-name=<kürzel>`.

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
| Idempotenzschlüssel | 1 bis 100 Zeichen | `XX-0007` |

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
| `UNAUTHORIZED` | praktisch nie: ohne Anmeldung antwortet schon die Sicherheitskette mit HTTP 401, bevor GraphQL läuft |
| `FORBIDDEN` | angemeldet, aber nicht berechtigt (`AA-…`, Statusregeln) |
| `NOT_FOUND` | eine Buchung, nach id gefragt, gibt es nicht **oder** sie ist nicht sichtbar; beides sieht gleich aus (`TR-0001`) |
| `CONFLICT` | die Version passt nicht (`XX-0003`) oder der Idempotenzschlüssel wurde mit anderer Eingabe verwendet (`XX-0006`) |
| `BAD_REQUEST` | eine fachliche Regel ist verletzt, z. B. kein Arbeitsbeginn am Tag (`TR-0024`), kein Vertrag (`TR-0002`) oder kein Mitarbeiterauftrag am Tag (`TR-0003`) |
| `INTERNAL_ERROR` | ein unerwarteter Fehler, ohne Details |

## Ändern: die gelesene Version mitschicken

Jede Buchung trägt eine `version`; `timereport(id: …)` liest sie für eine einzelne Buchung.
`updateTimereport` verlangt die Version, in der der Aufrufer die Buchung gelesen hat. Hat sie seitdem jemand anderes geändert, in der Oberfläche oder über ein anderes
Skript, kommt `XX-0003` / `CONFLICT` statt eines stillen Überschreibens. Dann neu lesen und die
Änderung erneut anwenden.

## Anlegen: ein Idempotenzschlüssel je Vorhaben

`createTimereport` verlangt einen `idempotencyKey` mit 1 bis 100 Zeichen, zum Beispiel eine UUID. Der Aufrufer erzeugt ihn
einmal je fachlichem Vorhaben und schickt ihn bei jeder Wiederholung unverändert mit.

- **Erster Aufruf:** legt an und antwortet mit `replayed: false`.
- **Wiederholung mit demselben Schlüssel und derselben Eingabe**, etwa nach einem Zeitlimit: legt
  nichts an und antwortet mit der damals angelegten Buchung und `replayed: true`.
- **Derselbe Schlüssel mit anderer Eingabe:** wird mit `XX-0006` / `CONFLICT` abgewiesen. Gleich ist,
  was dieselbe Buchung ergibt: Ein weggelassenes `ticketReferences` und `[]` sind dasselbe.
- **Wiederholung, nachdem die Buchung gelöscht wurde:** antwortet mit `TR-0001` / `NOT_FOUND` und
  legt nichts neu an.

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

Eine Buchung lesen und mit der gelesenen Version ändern:

```graphql
{ timereport(id: 123) { version duration status } }
```

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

In welchen Zeiträumen hat eine Person auf welchem Kundenauftrag gearbeitet? Ohne `employeeId` die
eigene Historie. Kundenaufträge sind die mit mindestens einem fakturierbaren Unterauftrag; interne
Aufträge, Urlaub und Krankheit stehen nicht darin. Ein Zeitraum reicht vom ersten bis zum letzten Buchungstag. Liegen 30 Tage oder mehr ohne
Buchung dazwischen, beginnt ein neuer Zeitraum, und der Auftrag steht ein weiteres Mal in der Liste.
Es zählen nur die Buchungen, die man auch in der Buchungsliste sieht:

```graphql
{
  orderHistory(employeeId: 42) {
    from until bookedDays duration
    customerorder { sign description customer { shortName segment { name } } }
  }
}
```
