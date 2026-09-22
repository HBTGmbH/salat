# ADR-0026 Betrieb als Azure Web App, Authentifizierung durch Azure EasyAuth

Date: 2026-09-18
Status: Accepted

## Context and Problem Statement

Salat wird als eine Anwendung deployt (→ ADR-0001) und braucht eine Laufzeitumgebung sowie eine
Entscheidung darüber, **wo** Authentifizierung stattfindet. Die Nutzerinnen und Nutzer sind
Mitarbeitende, die bereits eine Entra-ID-Identität haben; die Anwendung selbst kennt keine
Passwörter und will keine verwalten.

Davon getrennt ist die Frage der **Autorisierung**: Salat leitet Rollen aus dem
Mitarbeiterstatus in der eigenen Datenbank ab (→ ADR-0006), nicht aus Verzeichnisgruppen. Wer
authentifiziert ist, ist damit noch lange nicht berechtigt — und wer in Entra ID existiert, ist
nicht zwingend ein Salat-Benutzer.

Neben der Web-UI gibt es eine REST-API (`/api/**`, `/rest/**`), die von Skripten und Werkzeugen
außerhalb des Browsers gerufen wird. Für sie muss dieselbe Frage beantwortet werden.

## Considered Options

* **Option A** — Azure Web App mit **App Service Authentication (EasyAuth)** davor:
  Authentifizierung im Plattform-Proxy, Autorisierung in der Anwendung.
* **Option B** — Azure Web App **ohne** EasyAuth: Spring Security als OAuth2-Client, die
  Anwendung führt den Login-Flow gegen Entra ID selbst durch.
* **Option C** — Container-Plattform (AKS, Container Apps) mit eigenem Ingress und eigenem
  Auth-Proxy (oauth2-proxy o. ä.).

## Decision Outcome

Chosen: **Option A**, weil die Anmeldung damit vollständig aus dem Anwendungscode verschwindet:
kein Login-Formular, keine Client-Secrets im Deployment, keine Redirect- und Callback-Behandlung,
kein Refresh-Token-Handling. Die Anwendung bekommt Requests, die **bereits** eine geprüfte
Identität tragen, und kümmert sich nur noch um das, was sie allein weiß — welche Rechte dieser
Mensch in Salat hat.

Option B verlagert genau diesen Teil wieder in die Anwendung, ohne dass ein Bedürfnis dafür
besteht. Option C bringt eine Plattform mit, deren Betriebsaufwand für einen einzelnen
modularen Monolithen nicht gerechtfertigt ist.

### Verbindliche Konfiguration der Web App

| Einstellung | Wert |
|---|---|
| App Service authentication | Enabled |
| Restrict access | Require authentication |
| Unauthenticated requests | HTTP 302 Found (Redirect to identity provider) |
| Redirect to | Microsoft |
| Token store | Enabled |

Diese fünf Werte sind nicht Geschmackssache, sondern Voraussetzung dafür, dass die Anwendung
funktioniert:

* **Require authentication** ist das, was die Aussage „jeder Aufruf ist authentifiziert" trägt.
  Steht hier *Allow unauthenticated access*, reicht die Plattform anonyme Requests durch, und
  die Filter-Chains in `AzureEasyAuthSecurityConfiguration` sind die einzige verbliebene
  Verteidigung — mit einem Header-Vertrauensmodell, das ohne den Proxy davor nicht mehr trägt.
* **Token store: Enabled** erzeugt überhaupt erst den Header `x-ms-token-aad-id-token`. Ohne ihn
  hat der `JwtDecoder` nichts zu prüfen und jeder Request scheitert.
* **HTTP 302 Found** beschreibt nur den HTML-Fall. Was ein Aufrufer ohne Anmeldung tatsächlich
  zurückbekommt, entscheidet EasyAuth anhand des `Accept`-Headers — siehe *Consequences*.

**Das gilt für jede Umgebung mit EasyAuth, nicht nur für Produktion.** `staging` ist hier keine
abgeschwächte Variante: dieselben fünf Einstellungen in der Web App, dieselben Header- und
Claim-Namen unter `salat.auth.easy-auth`, und die Werte stehen im Profil, nicht in den App Settings.
Verschieden ist allein die App-Registrierung — `salat.auth.api-scope` und die springdoc-`client-id`
tragen je Umgebung deren eigene Client-ID. Jede Abweichung darüber hinaus ist ein Fehler und keine
Umgebungseigenheit: `application-staging.yaml` war bis #1049 auf einen Property-Baum verdrahtet, den
`SalatProperties` längst nicht mehr kannte, und der Zweig, den `AzureEasyAuthSecurityConfiguration`
dereferenziert, fehlte ganz.

### Aufgabenteilung

**EasyAuth** authentifiziert: es hält die Sitzung (Cookie `AppServiceAuthSession`), führt den
Anmeldefluss gegen Entra ID, legt die Tokens im Token Store ab und reicht sie als Header an die
Anwendung weiter — `x-ms-client-principal-id` und `x-ms-token-aad-id-token`.

**Die Anwendung** autorisiert. Sie liest das ID-Token über einen `BearerTokenResolver`, der
`x-ms-token-aad-id-token` vor `Authorization` prüft, zieht aus dem Claim `mailnickname` das
Salat-Kürzel, schlägt darüber den `loginStatus` nach und bildet ihn auf Spring-Authorities ab
(`EmployeeStatusAuthorities`). Was danach passiert, steht in ADR-0006.

Zwei Eigenheiten dieser Konstruktion sind bewusst so:

* Der `JwtDecoder` prüft die **Ablaufzeit nicht** (`JwtTypeValidator.jwt()` plus
  Principal-Abgleich, kein Timestamp-Validator). Die Gültigkeit der Sitzung verantwortet der
  Token Store, der die Tokens erneuert; ein Ablaufdatum in einem Token, das die Anwendung nie
  selbst ausgestellt hat und nie selbst erneuern kann, wäre nur eine zweite, schlechter
  informierte Meinung.
* Der `oid`-Claim im Token wird gegen den Header `x-ms-client-principal-id` gegengeprüft. Das
  fängt ab, dass Token und Header aus verschiedenen Anmeldungen stammen — es ersetzt aber
  **nicht** den Proxy: beide Werte sind Header und damit fälschbar, sobald jemand die Anwendung
  unter Umgehung von EasyAuth erreicht. Dass das auf App Service nicht möglich ist, weil EasyAuth
  als Middleware vor dem Container sitzt, ist die Prämisse, auf der das gesamte Modell steht.

### Auch die REST-API geht durch den Proxy

Es gibt keinen Seiteneingang. `/api/**` und `/rest/**` liegen hinter derselben EasyAuth-Instanz
wie die UI, also gilt für sie dieselbe Regel: **ein Client muss ein Authentifizierungsverfahren
benutzen, das EasyAuth unterstützt.** Praktisch sind das zwei:

1. das EasyAuth-Sitzungscookie — der Fall „API-Aufruf aus einer laufenden Browser-Sitzung",
   inklusive HTMX;
2. ein Entra-ID-Bearer-Token für den API-Scope, etwa über den Device-Code-Flow.

Beides ist in `AzureEasyAuthOpenApiConfiguration` als Security Scheme hinterlegt und damit in der
API-Dokumentation sichtbar.

Der zweite Weg hängt an einer Eigenschaft der Plattform, die man ihm nicht ansieht: der
`JwtDecoder` gleicht den `oid`-Claim gegen `x-ms-client-principal-id` ab, und dieser Header ist
eine Zutat von EasyAuth. Ein Bearer-Token allein genügt also nur, solange der Proxy den Header
auch für token-authentifizierte Requests setzt und mit derselben Objekt-ID füllt. Am 2026-09-19
gegen Produktion nachgemessen — Device-Code-Flow, Access Token für
`api://<client-id>/user_impersonation`, `GET /api/favorite` und `GET /rest/favorite` je 200. Fiele
der Header weg, bräche jeder Aufruf außerhalb des Browsers, erkennbar am Log `oid claim in jwt
does not match easy auth header`. Was es ausdrücklich **nicht** gibt: eigene API-Keys, Basic Auth,
technische Benutzer mit selbstvergebenen Tokens. Ein Integrationswunsch, der keine
Entra-ID-Identität beschaffen kann, ist damit nicht bedienbar — das ist der Preis der
Entscheidung, nicht ein Versehen.

### Consequences

* Good: kein Anmeldecode und keine Client-Secrets in der Anwendung; ein Wechsel des
  Identitätsanbieters ist Plattformkonfiguration, keine Codeänderung.
* Good: eine einzige Stelle, die authentifiziert, für UI und API gemeinsam. Ein neuer Endpunkt
  ist nicht versehentlich offen — er liegt automatisch hinter EasyAuth.
* Good: die Anwendung bleibt zustandslos (`STATELESS` in allen Filter-Chains, `AuthorizedUser`
  ist `@RequestScope`). Die Sitzung liegt im Proxy, nicht in der Instanz — Skalieren braucht
  keine Sitzungsaffinität.
* Neutral: **was ein nicht angemeldeter Aufruf zurückbekommt, hängt vom `Accept`-Header ab.**
  Die Einstellung *HTTP 302 Found* gilt nicht pauschal; EasyAuth verhandelt. Gemessen gegen
  Produktion am 2026-09-19:

  | Anfrage ohne Anmeldung | Antwort |
  |---|---|
  | `Accept: text/html` | 302 auf `login.windows.net/<tenant>/oauth2/…` |
  | `Accept: application/json`, `*/*` oder kein `Accept` | 401 mit `WWW-Authenticate: Bearer realm="salat.hbt.de" authorization_uri=…` |

  Für Aufrufer der REST-API ist das genau das richtige Verhalten: ein verwertbarer Fehler mit
  Challenge statt einer Weiterleitung. Auch ein HTMX-Aufruf sendet `Accept: */*` und bekommt
  deshalb 401 — wer eine Teilaktualisierung baut, muss diesen Status behandeln, nicht eine in ein
  Fragment gerenderte Anmeldeseite. Ungeprüft ist `hx-boost`: eine Volldokument-Navigation sendet
  `text/html` und dürfte damit in den Redirect laufen. All das ist Plattformverhalten, nicht
  Konfiguration — es kann sich mit App Service ändern, ohne dass hier jemand etwas umstellt.
* Bad: die Anwendung ist an App Service gebunden. Ein Umzug auf eine andere Plattform bedeutet
  nicht „Container woanders starten", sondern die Authentifizierung neu bauen — Option B oder C
  nachträglich.
* Bad: lokal gibt es kein EasyAuth. Deshalb existieren zwei weitere Profile: `local` mit
  Pre-Authenticated-Filter über `?login-name=<sign>` und `localeasyauth` für einen lokalen Lauf
  gegen echtes EasyAuth. Zwei Sicherheitskonfigurationen heißt: ein Fehler kann in einer davon
  stecken und in der anderen nicht auffallen.
* Neutral: die Abmeldung ist die EasyAuth-Route `/.auth/logout`, konfiguriert unter
  `salat.auth.logout.logout-url` und im Benutzermenü von `layout/base.html` verlinkt. Die
  Anwendung hat keinen eigenen Logout.
* Neutral: **die `permitAll`-Liste in der Filter-Chain `resources` bedeutet in Produktion etwas
  anderes, als ihr Name nahelegt.** Unauthentifiziert im Sinne von EasyAuth erreicht sie
  niemand — der Proxy leitet vorher um. „Noch nicht angemeldet" heißt dort: bei Entra ID
  authentifiziert, aber in Salat unbekannt (kein `loginStatus` zum Kürzel → 401 → Fehlerseite).
  Genau für diesen Fall müssen Wortmarke, JS und Webjars ohne gültigen Salat-Benutzer ladbar
  sein. Auch `/actuator/health` ist von außen nicht anonym erreichbar; Erreichbarkeitsprüfungen
  müssen entweder plattformintern laufen oder in EasyAuth ausgenommen werden.

## Beziehungen zu anderen ADRs

| ADR | Beziehung |
|---|---|
| [ADR-0001](0001-modular-monolith.md) | Ein Deployable → genau eine Web App, und damit genau eine EasyAuth-Konfiguration für alle Module. Würde je ein Modul herausgelöst, bräuchte es seine eigene Authentifizierung; diese ADR wäre dann neu zu stellen. |
| [ADR-0006](0006-rollenbasierte-autorisierung.md) | Die engste Beziehung, komplementär: EasyAuth beantwortet „wer", ADR-0006 beantwortet „darf". Der Abschnitt *Sicherheitskonfiguration* dort benennt `AzureEasyAuthSecurityConfiguration` bereits — diese ADR liefert die Begründung dazu. |
| [ADR-0013](0013-kein-httpsession-in-neuen-controllern.md) | Wechselseitig stützend. Weil kein UI-Zustand in der `HttpSession` liegt, bleibt die Instanz austauschbar — und weil EasyAuth die Sitzung hält, braucht die Anwendung gar keine. |
| [ADR-0018](0018-csrf-schutz-mit-cookie-tokenrepository.md) | Aufbauend. Der dort gewählte `CookieCsrfTokenRepository` ist gerade deshalb richtig, weil die Filter-Chains `STATELESS` sind — eine Folge dieser Betriebsform. Die CSRF-Chains gelten für die Profile `production`, `staging` und `localeasyauth`, also genau dort, wo EasyAuth aktiv ist. |
| [ADR-0015](0015-error-modul-fuer-fehlerseite.md) | Berührungspunkt: der oben beschriebene Fall „authentifiziert, aber kein Salat-Benutzer" landet auf der Fehlerseite. Deren Layoutressourcen sind der Grund für die `permitAll`-Liste. |
| [ADR-0020](0020-local-qa-profil-fuer-performancemessungen.md) | Ausdrückliche Ausnahme von der Paritätsregel: `application-local-qa.yaml` spiegelt Azure-EasyAuth und OAuth2 bewusst **nicht** — lokal gibt es keinen Azure-Login. Der Kommentar steht im Profil. |

## Beteiligte Klassen und Konfiguration

| Ort | Rolle |
|---|---|
| `auth/configuration/AzureEasyAuthSecurityConfiguration` | Drei Filter-Chains (Ressourcen / REST-API / Web-UI), `JwtDecoder`, `BearerTokenResolver`, Claim-auf-Rollen-Abbildung |
| `common/configuration/AzureEasyAuthOpenApiConfiguration` | Security Schemes `EasyAuth` (Cookie) und `oauth2` (Entra ID) in der API-Dokumentation |
| `common/SalatProperties.Auth.EasyAuth` | Header- und Claim-Namen als Konfiguration, nicht als Literal im Code |
| `application-production.yaml`, `application-staging.yaml` | `issuer-uri`, `salat.auth.easy-auth.*`, `salat.auth.logout.logout-url`, API-Scope |
| `application-localeasyauth.yaml` | Lokaler Lauf gegen echtes EasyAuth mit denselben Header- und Claim-Namen |
