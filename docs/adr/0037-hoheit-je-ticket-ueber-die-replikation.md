# ADR-0037 Hoheit je Ticket: die Replikation am Ticket sagt, wer es pflegt

Date: 2026-10-06
Status: Accepted

## Context and Problem Statement

`jira_ticket` war bis #1386 eine reine Replik: Was in einem Geltungsbereich (Auftrag, optional
Unterauftrag) stand, kam von der Replikation dieses Bereichs. Mit #1386 lassen sich Tickets auch von
Hand anlegen und aus einer Datei importieren, für Kunden ohne API-Zugang zu ihrem JIRA oder ganz
ohne JIRA, und neben einer Replikation für das, was sie (noch) nicht liefert. Ein Bereich darf
außerdem mehrere Replikationen haben, auch gegen verschiedene JIRA-Instanzen.

Damit stellt sich für jedes Ticket die Frage, wer es pflegt. Davon hängt ab, wer es ändern oder
löschen darf, welche Tickets eine Replikation entfernt, wenn ihre JQL sie nicht mehr findet, und
auf welche Tickets der Worklog-Abgleich zurückschreibt.

## Considered Options

* **A: Sperre je Bereich.** Wo eine Replikation den Bereich abdeckt, gibt es keine Pflege von Hand.
* **B: Hoheit je Ticket über einen Fremdschlüssel.** `jira_ticket.replication_id` nennt die
  Replikation, die das Ticket pflegt; `NULL` heißt von Hand gepflegt.
* **C: Herkunft als Kennzeichen.** Eine Spalte „repliziert ja/nein“, ohne zu sagen, welche
  Replikation.

## Decision Outcome

Chosen: **B**, auf Wunsch des Product Owners. A hätte Tickets, die eine Replikation nicht liefert,
in genau den Bereichen ausgeschlossen, in denen sie gebraucht werden. C kann zwei Replikationen
eines Bereichs nicht auseinanderhalten.

Die Regeln:

1. **Eine Replikation pflegt die Tickets, die auf sie zeigen.** Nur die darf sie ändern, nur die
   entfernt sie nach einem vollständigen Lauf, nur auf die schreibt ihr Worklog-Abgleich zurück.
2. **Ein Ticket ohne Hoheit übernimmt sie**, sobald sie seinen Schlüssel im selben Bereich liefert:
   von Hand angelegt, importiert oder von einer gelöschten Replikation zurückgelassen. Sie schreibt
   es dann, auch wenn JIRA es als unverändert meldet.
3. **Ein Ticket einer anderen Replikation überspringt sie**, die Hoheit bleibt. Der Text des Laufs
   nennt die Schlüssel samt pflegender Replikation.
4. **Löschen und Bereichswechsel einer Replikation geben ihre Tickets frei.** Das Löschen erledigt
   `ON DELETE SET NULL`, den Bereichswechsel `JiraReplicationConfigService`. Die Tickets bleiben
   stehen, sind von Hand pflegbar und werden nach Regel 2 übernommen.
5. **Eindeutig ist der Schlüssel je Bereich, die JIRA-ID je Replikation.** Zwei Replikationen eines
   Bereichs dürfen zwei Instanzen lesen, die dieselben numerischen ids vergeben. Einen Schlüssel
   hat ein Bereich nur einmal; liefern ihn zwei Replikationen, gilt Regel 3.
6. **Abgeleitete Werte gehören keinem.** `top_level_key` und `custom_fields_effective` werden für
   alle Tickets des Bereichs berechnet, nach jedem Lauf und bei jedem Schreiben von Hand, mit
   derselben Berechnung (`JiraTicketChains`). Vererbt werden die Felder aller Replikationen des
   Bereichs und die des letzten Imports.
7. **Zwei Replikationen desselben Bereichs laufen nacheinander** (Nachtrag zu ADR-0028), weil
   beide die abgeleiteten Werte aller Tickets des Bereichs schreiben.

### Consequences

* Good: Pflege von Hand und Replikation stehen nebeneinander, ohne dass Buchungsvorschläge,
  Ticketfilter oder Reports den Unterschied kennen müssen: alle lesen `jira_ticket` je Bereich.
* Good: Was eine Replikation löscht oder per Worklog beschreibt, ist auf ihre eigenen Tickets
  begrenzt, auch bei mehreren Replikationen im selben Bereich.
* Bad: Eine Replikation, die einen Schlüssel einer anderen liefert, schreibt ihn nicht. Das ist
  gewollt, verlangt aber, dass jemand die gemeldeten Schlüssel liest und die JQL anpasst.
* Neutral: Ein von Hand gepflegtes Ticket, dessen Schlüssel JIRA später einem umbenannten Issue
  gibt, weicht diesem Issue (`JiraReplicationService`).

## Verwandte Entscheidungen

* ADR-0024: Konfigurierbare Jira-Felder als JSON-Spalte (Nachtrag #1386)
* ADR-0028: Ein ETL-Lauf zur Zeit (Nachtrag #1386: Sperre je Bereich)
* ADR-0036: Bezüge über Modulgrenzen als Referenz
