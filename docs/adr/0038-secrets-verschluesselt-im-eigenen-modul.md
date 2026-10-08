# ADR-0038 Secrets liegen verschlüsselt im Modul `secret`, ausgehendes OAuth führt die Anwendung selbst

Date: 2026-10-08
Status: Proposed

## Context and Problem Statement

Die Jira-Replikation soll sich an Jira Cloud per OAuth anmelden können (#1417), statt mit einem
API-Token, das jemand von Hand erzeugt und einträgt. Dafür muss Salat Access- und Refresh-Tokens
speichern und selbst erneuern. Das kann es bisher nicht: Die Anwendung ist nur Resource Server, die
Anmeldung übernimmt EasyAuth (→ ADR-0026).

Dazu kommt ein bestehender Mangel. Die Replikation speichert ihr Secret (Passwort, Cloud-API-Token
oder Personal Access Token) im Klartext in `jira_replication_config.password`. Jede Kopie der
Datenbank, jedes Backup und jeder lesende Zugriff auf die Tabelle enthält es.

Zu entscheiden war in #1416 gemeinsam:

1. Woran hängt ein Secret, an der Replikation oder an der Person, die verbunden hat?
2. Eigenes Modul oder Teil von `jira`, eigene Tabelle oder das Schema von Spring Security?
3. Wie wird verschlüsselt, woher kommt der Schlüssel?
4. Was passiert mit Secrets in einer lokalen Kopie der Datenbank?
5. Wie überlebt ein rotierendes Refresh-Token zwei gleichzeitige Erneuerungen?
6. Was passiert bei Ablauf, Widerruf, Trennen und Löschen?
7. Wo liegt die Client-Registrierung?
8. Wohin mit `state` und PKCE, ohne `HttpSession` (→ ADR-0013)?
9. Was darf in Logs, Fehlermeldungen und die Laufhistorie?
10. Ziehen die bestehenden Klartext-Secrets mit um?

## Considered Options

Für den Ort der Secrets (Fragen 2 bis 4):

* **A: Key Vault, Secrets direkt dort.** Keine eigene Verschlüsselung, in der Datenbank steht
  nichts.
* **B: Verschlüsselt in der Datenbank, der Schlüssel aus dem Secret-Speicher der
  Betriebsumgebung.** Die Anwendung verschlüsselt selbst.
* **C: Credential Manager eines API-Gateways.** Zustimmung, Erneuerung und Speicherung der Tokens
  übernimmt eine Plattformkomponente.
* **D: Token Store von EasyAuth.**
* **E: Nur Verschlüsselung der Datenbank im Ruhezustand.**

Für das Modell (Frage 2):

* **F: Tabelle `oauth2_authorized_client` mit `JdbcOAuth2AuthorizedClientService`** von Spring
  Security.
* **G: Eigene Tabelle in einem eigenen Modul, für jede Art von Secret.**
* **H: Eigene Spalten oder Tabelle im Modul `jira`.**

## Decision Outcome

Chosen: **B** und **G**.

A scheitert an der Rotation: Ein Secret im Vault lässt sich nicht bedingt schreiben, die Sperre
gehörte also trotzdem in die Datenbank. Jede Erneuerung erzeugte außerdem stündlich eine neue
Version, die Anwendung bräuchte Schreibrecht im Vault, und lokal bräuchte es einen zweiten Weg.
C bringt eine Plattformkomponente mit eigenem Betrieb und eigenen Kosten, mit demselben Argument,
mit dem ADR-0026 Option C verworfen hat. Erneut prüfen, wenn Verbindungen zu vielen Anbietern
dazukommen. D hält nur Tokens des Login-Anbieters, an die Sitzung gebunden. Der stündliche Lauf ohne
angemeldete Person kommt nicht heran. E schützt Platten und Backups, eine Kopie der Datenbank aber
nicht.

F legt die Tokens im Klartext ab, schlüsselt nach Person (Principal) und kennt weder
Versionsnummer noch Schlüsselkennung noch einen Zustand „neu verbinden“. H bindet, was jede
Integration braucht, an eine einzige. Verbindungen je Person (Connected Accounts) und die
bestehenden Klartext-Secrets müssten später wieder herausgelöst werden.

### 1. Ein Secret kennt seinen Eigentümer nicht

Das Modul `secret` speichert Secrets und gibt sie über ihre id heraus. **Wer ein Secret braucht,
merkt sich die id**, die Replikation in `jira_replication_config.secret_id`, abhängig von ihrer
Anmeldeart. Eine Verbindung je Person käme später mit einem eigenen Fremdschlüssel beim Eigentümer
dazu, ohne das Modul zu ändern.

Ein Secret hat eine Art, und jede Art ist eine Einheit, die zusammen geschrieben wird:

| Art | Inhalt | Heute genutzt von |
|---|---|---|
| `USERNAME_PASSWORD` | Benutzername und Passwort | Replikation mit `BASIC` (Server: Passwort, Cloud: E-Mail und API-Token) |
| `TOKEN` | ein Token | Replikation mit `PERSONAL_ACCESS_TOKEN` |
| `KEY` | Schlüssel mit Kennung | noch niemand; kommt mit dem ersten Nutzer |
| `OAUTH` | Access-Token mit Ablauf, Refresh-Token, Scopes, fremdes Konto | Replikation mit OAuth (#1417) |

Access- und Refresh-Token sind **ein** Secret, nicht zwei, weil sie nur zusammen erneuert werden.
Der Benutzername gehört mit ins Secret: Bei Cloud ist er die E-Mail-Adresse des Kontos, und er ist
ohne das Passwort nutzlos.

Bei OAuth handelt die Replikation als das verbundene Konto, nicht als die Person, die verbunden
hat. Das Secret hält beides fest: das fremde Konto (Kennung und Name, für die Anzeige) und das
Salat-Kürzel derjenigen Person, die verbunden hat, mit dem Zeitpunkt.

### 2. Modul und Modell

Tabelle `secret` im Modul `secret`:

| Spalte | Inhalt |
|---|---|
| `id` | |
| `type` | Art, Klartext |
| `status` | `VALID` oder `REAUTH_REQUIRED`, Klartext |
| `key_id` | Kennung des Schlüssels, mit dem `payload` verschlüsselt ist |
| `payload` | `varbinary`: IV, danach Chiffrat des Inhalts der Art |
| `version` | Versionsnummer (→ ADR-0033) |
| Audit-Spalten | über `AuditedEntity` |

Alles außer Art und Zustand ist verschlüsselt, auch Benutzername und Konto. Für die Anzeige gibt
der Service eine Zusammenfassung ohne das eigentliche Secret heraus.

Die Entität verlässt das Modul nie, der Inhalt nur entschlüsselt über `SecretService`. Deshalb
verweist der Eigentümer **über die id mit Fremdschlüssel, nicht über `@ManyToOne`**, abweichend von
der Regel für Stammdaten in ADR-0036: Eine Referenz lüde eine Entität, deren Felder ohne den
Service nichts bedeuten.

Nach ADR-0011 sind Secrets **Stammdaten** ihres Eigentümers: langlebig und von Konfiguration
referenziert. Sie haben kein `hide`, sondern einen Zustand, und sie werden **hart gelöscht**. Ein
soft-gelöschtes Secret wäre ein Secret, das weiter gespeichert ist.

Von Spring Security OAuth2 Client nutzt die Anwendung nur die Bausteine für den HTTP-Austausch:
`ClientRegistration`, die Token-Response-Clients für Code-Tausch und Refresh, PKCE. Nicht genutzt
wird `oauth2Client()` mit seinem Filter: er schlüsselt den autorisierten Client nach dem
angemeldeten Principal, hier aber gehört er einer Replikation.

### 3. Verschlüsselung

* **AES-256-GCM** mit zufälligem 96-Bit-IV je Wert. Ein symmetrischer Schlüssel genügt: Dieselbe
  Anwendung verschlüsselt und entschlüsselt, ein Schlüsselpaar brächte keinen Schutz.
* **Associated Data `secret:<id>:<type>`.** Ein Chiffrat, das in eine andere Zeile kopiert wird,
  scheitert beim Entschlüsseln, statt still das Secret einer anderen Verbindung zu liefern.
* **Kein Passwort auf dem Schlüssel.** Es läge am selben Ort und schützte gegen nichts.
* **Schlüsselkennung je Zeile ab dem ersten Datensatz.** Neue Werte nutzen den aktiven Schlüssel,
  ältere bleiben mit ihrem lesbar. Beim Start verschlüsselt die Anwendung Zeilen mit einem
  abgelösten Schlüssel neu, danach kann er aus der Konfiguration entfallen.

Der Schlüssel kommt aus dem Secret-Speicher der Betriebsumgebung als Umgebungsvariable, nie aus der
Datenbank, nie aus `application*.yaml`, nie aus dem Repository:

```
SALAT_SECRET_ACTIVEKEYID=k1
SALAT_SECRET_KEYS_K1=<openssl rand -base64 32>
```

Gebunden unter `salat.secret` in `SalatProperties`. Kennungen nur aus Kleinbuchstaben und Ziffern,
denn Spring macht aus einem Unterstrich im Namen der Variablen einen Punkt.

* **Fehlerhafter Schlüssel** (nicht 256 Bit, kein Base64, aktive Kennung ohne Schlüssel) verhindert
  den Start, damit der Fehler beim Deployment auffällt.
* **Ohne Schlüssel** startet die Anwendung, speichert aber kein Secret, auch nicht im Klartext.
  Formulare bieten die Eingabe nicht an, und ein Lauf, der ein Secret braucht, scheitert mit einer
  Meldung, die das sagt.
* **Zeile mit unbekannter Kennung oder Chiffrat, das sich nicht entschlüsseln lässt**: Das Secret
  ist nicht lesbar. Eine OAuth-Verbindung geht auf `REAUTH_REQUIRED`, ein Passwort oder Token muss
  neu eingegeben werden.

### 4. Lokale Kopien

Je Umgebung gilt ein eigener Schlüssel. Eine Kopie der Datenbank enthält nur Chiffrat. Lokal ist es
nicht lesbar, und wer dort eine Replikation laufen lassen will, verbindet neu oder gibt das Secret
neu ein. Das ist gewollt.

### 5. Rotierende Refresh-Tokens

Ein Versionsvergleich beim Schreiben allein reicht nicht: Wenn zwei Erneuerungen gleichzeitig
starten, haben beide das alte Refresh-Token schon an den Anbieter geschickt, bevor eine beim
Speichern scheitert. Ein Anbieter, der die Wiederverwendung erkennt, widerruft dann womöglich alle.
Deshalb gilt **gegenseitiger Ausschluss vor dem Aufruf**:

1. Ist das Access-Token noch mindestens fünf Minuten gültig, wird es benutzt.
2. Sonst nimmt der Aufrufer die Sperre dieses Secrets, eine Sperre je id in der JVM.
3. In der Sperre liest er die Zeile neu. Hat inzwischen ein anderer erneuert, nimmt er dessen Token.
4. Sonst erneuert er. Während des HTTP-Aufrufs hält er keine Datenbankverbindung.
5. Er schreibt beide Tokens in einer kurzen Transaktion, mit der Versionsnummer als Sicherheitsnetz.

Wie in ADR-0028 setzt die Sperre **eine Instanz** voraus. Wird diese Voraussetzung aufgegeben, ist
der nächste Schritt eine Lease-Spalte mit bedingtem `UPDATE`, gemeinsam mit den übrigen Sperren der
Anwendung.

Stirbt der Prozess zwischen Erneuerung und Schreiben, ist das neue Refresh-Token verloren und die
Verbindung muss neu hergestellt werden. Das lässt sich nicht verhindern, nur sichtbar machen.

### 6. Ablauf, Widerruf, Trennen, Löschen

* **`invalid_grant`** beim Erneuern setzt das Secret auf `REAUTH_REQUIRED` und entfernt die Tokens
  aus dem Inhalt. Der Lauf scheitert mit „Verbindung abgelaufen – bitte neu verbinden“, das
  Formular zeigt den Zustand.
* **Trennen** löscht das Secret und leert den Verweis beim Eigentümer, in derselben Transaktion.
* **Löschen einer Replikation** löscht ihr Secret mit. Ein anderer Weg dorthin existiert nicht: Das
  Löschen von Auftrag oder Unterauftrag wird abgewiesen, solange eine Replikation darauf zeigt
  (`JiraScopeDeleteListener`).
* **Widerruf beim Anbieter** erfolgt nach dem Commit, wenn der Anbieter einen
  Revocation-Endpunkt anbietet. Scheitert er, wird das protokolliert, das Löschen bleibt bestehen.
  Ohne Endpunkt sagt das Formular, wo man den Zugriff der App im fremden Konto entzieht.
* **Der Eigentümer verantwortet das Löschen.** Das Modul `secret` weiß nicht, wer ein Secret
  referenziert, und erkennt verwaiste Secrets deshalb nicht von selbst.

### 7. Client-Registrierung

Client-ID und Client-Secret der App beim Anbieter kommen wie der Schlüssel als Umgebungsvariable
aus dem Secret-Speicher, nicht aus der Datenbank. **Je Umgebung gibt es eine eigene App-Registrierung**
und damit ein eigenes Client-Secret. Die Redirect-URI steht je Profil in der Konfiguration und wird
nicht aus dem Request abgeleitet, weil hinter dem Proxy der Host nicht der öffentliche sein muss.
Fehlt die Registrierung, bietet das Formular „Verbinden“ nicht an.

Die Routen für Start und Callback gehören dem Eigentümer (die Replikation: ihr Controller), weil er
die Berechtigung kennt. Das Modul `secret` liefert den Ablauf dazwischen: Autorisierungsanfrage
bauen, Callback prüfen, Code tauschen, Secret anlegen.

### 8. `state` und PKCE im verschlüsselten Cookie

ADR-0013 erlaubt Zustand im Cookie. `state`, `code_verifier`, die Bindung an den Eigentümer (z. B.
`jira-replication:<id>`), das Kürzel der angemeldeten Person und der Ablaufzeitpunkt liegen in einem
Cookie:

* verschlüsselt mit dem Schlüssel aus Abschnitt 3, Associated Data `oauth-state`,
* Präfix `__Host-`, `HttpOnly`, `Secure`, **`SameSite=Lax`**, höchstens zehn Minuten gültig.
  `Strict` ginge nicht: Der Callback ist eine Navigation von der Site des Anbieters.

Der Callback weist ab, wenn eines davon nicht stimmt: Cookie fehlt oder ist abgelaufen, `state`
weicht ab, Person oder Eigentümer passen nicht. Er löscht das Cookie und leitet sofort weiter,
damit der `code` nicht in Verlauf und Referer stehen bleibt.

Zwei Verbindungsversuche zur selben Zeit in zwei Tabs überschreiben sich: Der erste scheitert dann
mit einer verständlichen Meldung. Eine Tabelle mit Aufräumjob wäre der Preis, das zu vermeiden.

### 9. Protokollierung

* Jede Art von Secret ist ein Typ, dessen `toString()` das Secret auslässt, wie `JiraCredentials`.
* Von einer Fehlerantwort des Token-Endpunkts wird nur der Fehlercode (`error`) protokolliert, nie
  der Body.
* Die Redaktion von Fehlermeldungen (`JiraCredentialRedaction`) nimmt jedes Secret heraus, das der
  Aufruf benutzt hat, auch das Access-Token.
* Verbinden, Trennen und das Ändern eines Secrets protokolliert die Anwendung mit Kürzel und id,
  ohne den Inhalt.
* Der `code` erscheint in den Zugriffsprotokollen der Plattform, weil er in der Adresse des
  Callbacks steht. Das ist hinnehmbar: Er ist nur einmal verwendbar, kurzlebig und ohne den
  `code_verifier` wertlos.

### 10. Die Klartext-Secrets ziehen im selben Schritt um

Die Umsetzung des Moduls stellt auch die bestehenden Replikationen um. Liquibase kennt den
Schlüssel nicht, deshalb läuft die Umstellung beim Start in Java: Für jede Replikation mit Passwort
und ohne `secret_id` wird ein Secret angelegt, der Verweis gesetzt und das Klartext-Secret geleert.
Die Umstellung ist wiederholbar und läuft nur mit Schlüssel. Die Spalten `username` und `password`
entfallen in einem späteren Release, wenn die Umstellung in Produktion gelaufen ist.

### Bedrohungen

Der Speicher schützt gegen:

* eine Kopie oder einen Abzug der Datenbank,
* Backups,
* jeden lesenden Zugriff auf die Datenbank, der den Schlüssel nicht hat,
* Secrets in Logs, Fehlermeldungen, `toString()` und Laufhistorie,
* vertauschte Chiffrate zwischen Zeilen (Associated Data).

Er schützt nicht gegen:

* eine kompromittierte laufende Anwendung: Sie hat den Schlüssel im Speicher,
* Zugriff auf den Secret-Speicher oder die Einstellungen der Betriebsumgebung,
* Personen, die eine Replikation bearbeiten dürfen: Sie handeln über die Anwendung als das
  verbundene Konto, ohne das Secret je zu sehen,
* Schreibzugriff auf die Datenbank: Wer schreiben kann, kann Secrets löschen oder Verweise
  umbiegen, aber keines lesen.

### Consequences

* Good: In der Datenbank steht kein Secret mehr im Klartext, auch nicht das bestehende der
  Replikation.
* Good: Eine neue Integration oder eine Verbindung je Person bringt keinen neuen Speicher mit,
  sondern einen Fremdschlüssel.
* Good: Erneuerung und Sperre liegen an einer Stelle, nicht in jedem Client.
* Bad: Ohne Schlüssel laufen Replikationen nicht mehr. Der Schlüssel muss vor dem Release in der
  Umgebung stehen, das gehört in die Betriebsschritte.
* Bad: Geht der Schlüssel verloren, müssen alle Verbindungen neu hergestellt und alle Passwörter
  und Tokens neu eingegeben werden. Eine Sicherungskopie außerhalb der Betriebsumgebung ist Teil der
  Betriebsschritte.
* Bad: Ausgehend kehrt zurück, was ADR-0026 aus der Anwendung herausgehalten hatte:
  Client-Secrets im Deployment, Callback-Behandlung, Erneuerung von Refresh-Tokens.
* Neutral: Die Sperre aus Abschnitt 5 setzt eine Instanz voraus, wie die aus ADR-0028.
* Neutral: Lokal gelten eigene Schlüssel. Mit einer Kopie der Produktionsdatenbank sind deren
  Secrets lokal nicht lesbar.

### Betriebsschritte

Neutral beschrieben; die konkreten Namen stehen in der Betriebsdokumentation.

1. Je Umgebung einen Schlüssel erzeugen, im Secret-Speicher der Betriebsumgebung ablegen und sicher
   außerhalb davon hinterlegen.
2. Der Anwendung `SALAT_SECRET_ACTIVEKEYID` und `SALAT_SECRET_KEYS_<ID>` bereitstellen, vor dem
   Release, das die Klartext-Secrets umstellt.
3. Für OAuth (#1417): je Umgebung eine App beim Anbieter registrieren, Redirect-URI eintragen,
   Client-ID und Client-Secret wie den Schlüssel bereitstellen.
4. Schlüsselwechsel: neuen Schlüssel ergänzen, aktive Kennung umstellen, neu starten. Den alten
   erst entfernen, wenn der Start alle Zeilen neu verschlüsselt hat.

## Beziehungen zu anderen ADRs

| ADR | Beziehung |
|---|---|
| [ADR-0026](0026-azure-web-app-mit-easyauth-als-authentifizierungsproxy.md) | Ergänzt sie in der Gegenrichtung. Die Anmeldung an Salat bleibt bei EasyAuth. Für ausgehende Verbindungen führt die Anwendung OAuth selbst, mit Client-Secret und Erneuerung. Der Token Store von EasyAuth taugt dafür nicht. |
| [ADR-0013](0013-kein-httpsession-in-neuen-controllern.md) | Angewandt, nicht geändert: `state` und PKCE liegen im Cookie, das sie dafür vorsieht. |
| [ADR-0028](0028-ein-etl-lauf-zur-zeit.md) | Dieselbe Voraussetzung einer Instanz, hier für die Sperre der Erneuerung. |
| [ADR-0033](0033-gleichzeitige-aenderung-wird-zum-fachlichen-befund.md) | Die Versionsnummer an `secret` ist das Sicherheitsnetz hinter der Sperre. |
| [ADR-0036](0036-stammdaten-ueber-modulgrenzen-als-referenz.md) | Bewusste Abweichung: Auf ein Secret wird über die id mit Fremdschlüssel verwiesen, nicht über `@ManyToOne`, weil seine Entität ohne den Service nichts bedeutet. |
| [ADR-0011](0011-stammdaten-vs-bewegungsdaten.md) | `Secret` ist Stammdatum mit Zustand statt `hide` und wird hart gelöscht. Die Tabelle dort zieht mit der Umsetzung nach. |
| [ADR-0003](0003-spring-events-fuer-cross-module-kommunikation.md) | Nicht nötig: Eigentümer rufen `secret` in der erlaubten Importrichtung direkt, `secret` kennt keinen Eigentümer. |
