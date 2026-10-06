# Idee: Issue-Tracker-Integration

Stand: 06.10.2026 · Status: **Idee, bewertet**. Keine Entscheidung, keine Umsetzung.

Dieses Dokument hält einen externen Konzeptentwurf zur Anbindung mehrerer Issue-Tracker fest und
bewertet ihn gegen den heutigen Stand von Salat. Am Ende steht ein Vorschlag, wie Salat sich in
kleinen Schritten in diese Richtung bewegen könnte.

---

## 1. Der Entwurf in Kürze

Der Entwurf will die heutige Jira-Anbindung durch eine allgemeine Integration ersetzen:

- **Mehrere Systeme** hinter einer gemeinsamen Connector-Schnittstelle: Jira Cloud, Jira Data
  Center, Tempo, GitLab, GitHub, Linear, YouTrack, Azure DevOps, Redmine, Plane. Jeder Connector
  gibt seine Fähigkeiten an (Worklogs schreiben ja/nein, im Namen anderer, Webhooks, …), und die
  Oberfläche richtet sich danach.
- **Personalisierte Rückschreibung:** Worklogs entstehen im Namen der buchenden Person, per OAuth
  oder persönlichem Token. Einstellbar sind die Modi `off`, `service_only`,
  `personal_preferred` und `personal_required`.
- **Datenmodell:** `connection` (Instanz eines Systems), `project_binding` (Projekt ↔ Verbindung
  mit Scope und Rückschreibeinstellungen), `credential` (technisch oder persönlich, verschlüsselt),
  `external_user_mapping`, `external_issue` (mit stabiler externer ID), `worklog_link` (eine Zeile
  je Buchung und Ziel), `sync_cursor`, `agent`.
- **Rückschreibung über eine Outbox:** Jede Änderung an einer Buchung erzeugt einen Job, Jobs sind
  idempotent über einen Marker (Buchungs-ID als Worklog-Property).
- **Read-Sync** inkrementell, ergänzt um Webhooks und einen nächtlichen Voll-Sync. Verschwundene
  Tickets werden markiert statt gelöscht.
- **On-Premise** über einen Agenten im Kundennetz (Docker oder Windows-Dienst, nur ausgehende
  Verbindungen), alternativ über IP-Freigabe oder Datei-Import.
- **Phasen:** 1. Abstraktion nur für Jira, 2. Personalisierung und Tempo, 3. weitere
  Cloud-Systeme, 4. On-Premise mit Agent, 5. Konflikterkennung und aggregierte Rückschreibung.

Der Entwurf ist sorgfältig gebaut: Fähigkeitsmatrix, gemeinsame Fehlertaxonomie (`auth_expired`,
`rate_limited`, `validation`, …), Sperre beim Token-Refresh mit atomarer Speicherung des
rotierenden Refresh-Tokens, Verfügbarkeit statt Löschen. Er ist aber für ein Produkt mit vielen
Mandanten geschrieben, nicht für eine einzelne interne Anwendung, und seine Ausgangslage trifft
Salat an mehreren Stellen nicht.

## 2. Ausgangslage im Abgleich mit Salat

| Annahme im Entwurf | Tatsächlich in Salat |
|---|---|
| Ein Jira-Account je Projekt | Eine Replikation je Auftrag oder Unterauftrag (`JiraReplicationConfig`), jede mit eigener Basis-URL und eigenen Zugangsdaten. Eine Verbindung, die mehrere Replikationen teilen, gibt es nicht. |
| Jira Data Center kommt erst mit dem Agenten (Phase 4) | Gibt es schon: `JiraApiFlavor.SERVER`, seit #1385 auch mit Personal Access Token, direkt angebunden. |
| Zurückgeschriebene Worklogs sind nicht personalisiert | Stimmt, und darüber hinaus aggregiert: `JiraWorklogSyncService` schreibt **einen Worklog je Tag und Ticket** mit der Summe über alle Buchenden (#1007). Die „aggregierte Rückschreibung ohne Personenbezug“ aus Phase 5 ist der heutige Zustand. |
| Eine Buchung verweist auf ein Ticket | Eine Buchung trägt beliebig viele Ticketverweise (#1326). `TicketDaySums` teilt die Minuten gleichmäßig auf. |
| Excel-Import als Ausweg | Import und manuelle Pflege gibt es seit #1386. Die Hoheit über ein Ticket liegt in `jira_ticket.replication_id`: gesetzt heißt repliziert, leer heißt von Hand gepflegt. |
| `external_issue` mit Titel, Status, Typ, Labels, Parent | Salat hat mehr: konfigurierbare Zusatzfelder als JSON (ADR-0024), über die Parent-Kette geerbte Felder (#881), `top_level_key`, Ticketvorschläge und Ticketketten. Ohne sie verfehlt der Entwurf sein eigenes Ziel „ohne Funktionsverlust“. |
| Credentials verschlüsselt (Envelope Encryption) | Heute liegen Passwort und Token unverschlüsselt in `jira_replication_config.password`. Das lässt sich unabhängig von allem anderen verbessern. |

## 3. Wo der Entwurf bewusste Entscheidungen in Salat umkehrt

### 3.1 Stabile externe IDs statt Keys

Der Entwurf lässt Buchungen auf die interne ID eines Tickets verweisen. Salat speichert den Key
bewusst als Text, die Begründung steht am Feld `Timereport.ticketReferences`:

- Ein Verweis wird getippt und darf ein Ticket nennen, das (noch) nicht repliziert ist.
- Derselbe Key kann unter mehreren Scopes repliziert sein, es gibt also keine einzelne Zeile, auf
  die man zeigen könnte.
- Ein Ticket kann verschwinden und unter einer neuen Zeile wiederkommen (#1167).

Der Entwurf löst nur den dritten Punkt (über `availability`). **Vorschlag:** Der Key bleibt der
Verweis der Buchung. Die Jira-ID (`jira_ticket.jira_id`) dient als Auflösungshilfe, mit der sich ein
in Jira verschobenes Ticket wiedererkennen lässt.

### 3.2 Outbox statt Summenabgleich

Salat vergleicht bei jedem Lauf die vollständigen Summen des Zeitraums mit dem zuletzt
Geschriebenen (`jira_worklog_sync`). Das ist Absicht: Eine Buchung wird nur als gelöscht markiert,
und das Löschen bewegt keinen Zeitstempel. Ein Summenabgleich bemerkt das von selbst. Eine
Outbox bräuchte dagegen einen Haken an jeder Stelle, die Buchungen ändert, darunter:

- Massen-Updates per JPQL,
- das Umschalten „nur abrechenbare Unteraufträge“ (#1218),
- Verschiebungen im Auftragsbaum (`JiraScopeMoveListener`),
- Korrekturen über ETL.

Jede vergessene Stelle führt zu einer stillen Abweichung zwischen Salat und Jira.

**Vorschlag:** Beim Abgleich gegen einen Soll-Zustand bleiben. Für personalisierte Worklogs wird
der Schlüssel `(Person, Tag, Ticket)` statt `(Scope, Tag, Ticket)`. Das ist von sich aus
idempotent und heilt Lücken beim nächsten Lauf. `worklog_link` aus dem Entwurf ginge dann in einer
erweiterten `jira_worklog_sync` auf, die Outbox entfällt.

### 3.3 Konflikte mit ADRs

- **ADR-0026** (EasyAuth, keine Client-Secrets und Refresh-Tokens in der Anwendung): Persönliches
  OAuth holt beides in die Anwendung zurück, wenn auch nur für ausgehende Aufrufe. Dafür braucht
  es ein neues ADR.
- **ADR-0013** (keine `HttpSession`): Der OAuth-State zwischen Weiterleitung und Callback braucht
  eine eigene Ablage, als Cookie oder in der Datenbank.
- **Webhooks** werden von EasyAuth abgewiesen, solange ihr Pfad nicht von der Authentifizierung
  ausgenommen ist. Das wäre eine Betriebsänderung mit eigenem Prüfbedarf (Signatur des Absenders).

## 4. Lücken im Entwurf

1. **Wer korrigiert, ist nicht wer gebucht hat.** In Salat ändern Manager und Backoffice die
   Buchungen anderer. Einen nativen Jira-Worklog darf nur sein Autor ändern, oder wer das Recht
   hat, alle Worklogs zu bearbeiten. Bei `personal_required` scheitert damit jede Korrektur durch
   Dritte. Es muss festgelegt werden, mit wessen Zugang geändert und gelöscht wird.
2. **Selten Buchende und Ausgeschiedene.** Refresh-Tokens verfallen bei Inaktivität, spätestens
   aber mit dem Austritt einer Person. Korrekturen an ihren alten Buchungen blieben dauerhaft
   `blocked`. Hier fehlt eine Rückfallregel, etwa „nach Austritt technischer Account“.
3. **Ticketwechsel heißt alten Worklog löschen, neuen anlegen.** Das Löschen braucht einen
   gültigen Zugang. Fehlt er, bleibt in Jira ein verwaister Worklog stehen.
4. **Mehrere Ticketverweise je Buchung** sind nicht modelliert. „Eine Zeile je Buchung und Ziel“
   reicht nicht, das Ticket gehört in den Schlüssel, und die Aufteilung der Minuten muss
   festgelegt sein (heute: gleichmäßig, Rest nach vorn).
5. **Mengen.** Ein Worklog je Buchung statt je Tag und Ticket vervielfacht die Schreibaufrufe.
   `findWorklogByMarker` muss alle Worklogs eines Tickets samt Properties lesen, was bei langen
   Tickets teuer wird. Beides wirkt direkt auf die Rate Limits.
6. **Sichtbarkeit beim Read-Sync.** Über den technischen Account sehen alle Buchenden Tickettitel,
   die sie in Jira selbst nicht sehen dürften. Salat macht das heute genauso. In einem Entwurf mit
   eigenem Datenschutzkapitel gehört es benannt.
7. **Datenschutz.** Der Hinweis auf § 87 BetrVG betrifft Salat nicht, weil es keinen Betriebsrat
   gibt. Die DSGVO-Frage bleibt: Personalisierte Worklogs übermitteln personenbezogene
   Leistungsdaten an den Kunden, das muss mit den Auftraggebern geklärt sein.

## 5. Was für Salat überdimensioniert ist

- `tenant_id` und Mandantenfähigkeit: Salat ist eine Anwendung für ein Unternehmen.
- Der Agent im Kundennetz: ein eigenes Produkt mit Protokoll, Registrierung, Auslieferung und
  Versionspflege, ohne dass heute ein Bedarf bekannt ist. Data Center läuft bereits direkt.
- IP-Freigabe mit festen Egress-IPs, Site-to-Site-VPN.
- Envelope Encryption mit eigenem KMS über einen Schlüssel aus dem Key Vault hinaus.
- Forge-App und Marketplace.
- Sieben weitere Connectoren auf Vorrat.

Die offene Frage des Entwurfs „Welche Systeme werden tatsächlich angefragt?“ muss vor jeder
Abstraktion beantwortet sein. Ohne zweiten konkreten Anwendungsfall ist die Connector-Schnittstelle
geraten, und das Datenmodell richtet sich nach Systemen, die nie kommen.

## 6. Vorschlag: Weg in kleinen Schritten

Jeder Schritt bringt für sich einen Nutzen und kann der letzte sein.

1. **Verbindung herauslösen.** Basis-URL, API-Variante, Anmeldeverfahren und Zugangsdaten als eigene
   Entität, die mehrere Replikationen teilen. Das Secret wird verschlüsselt gespeichert. Klein,
   und es beseitigt die mehrfach gepflegten Zugangsdaten.
2. **Personalisierte Rückschreibung für Jira Cloud.** Als Option je Replikation, mit Summenabgleich
   je `(Person, Tag, Ticket)` (→ 3.2). Vorher ein ADR zu OAuth, Token-Ablage und OAuth-State
   (→ 3.3), und die Fragen aus 4.1 bis 4.3 beantworten.
3. **Weitere Systeme nur bei konkretem Bedarf.** Dann die Connector-Schnittstelle aus dem
   vorhandenen Paar `JiraSearchClient`/`JiraWorklogClient` ableiten, statt sie vorab zu entwerfen.

## 7. Fragen, die Salat bereits beantwortet hat

- **Ticketdaten je Mandant oder je Bindung?** Je Scope, also je Auftrag oder Unterauftrag.
  Derselbe Key darf unter mehreren Scopes stehen (#1323, #1372).
- **Was passiert mit Tickets, wenn die Anbindung wegfällt?** Tickets überleben ihre Replikation,
  eine neue Replikation auf demselben Scope übernimmt sie über den Key (#1025, #1386).
- **Welches System führt bei Zeiten?** Salat. Worklogs, die jemand in Jira von Hand anlegt, rührt
  der Abgleich nicht an (#1007).

## 8. Offene Fragen

1. Gibt es einen konkreten Bedarf an personalisierten Worklogs, und bei welchen Auftraggebern?
2. Gibt es einen konkreten Bedarf an einem zweiten System neben Jira, und an welchem?
3. Mit wessen Zugang werden Buchungen Dritter korrigiert (→ 4.1)?
4. Was gilt für Buchungen von Personen, deren Zugang verfallen ist oder die ausgeschieden sind
   (→ 4.2)?
5. Soll der Read-Sync weiterhin alle Tickets eines Scopes für alle Buchenden sichtbar machen
   (→ 4.6)?

## 9. Verwandte Idee

**Connected Accounts:** Personen verknüpfen Fremdsysteme (Kalender, GitHub, GitLab, Jira) lesend
per delegiertem OAuth, und Salat schlägt daraus Buchungen vor. Beide Ideen brauchen dieselbe
Grundlage: persönliche OAuth-Verbindungen mit verschlüsselter Token-Ablage, rotierenden
Refresh-Tokens und einem Status „neu verbinden“. Das ADR aus Schritt 2 sollte beide Fälle abdecken.
