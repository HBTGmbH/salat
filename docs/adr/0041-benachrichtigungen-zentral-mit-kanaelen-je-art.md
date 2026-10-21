# ADR-0041 Benachrichtigungen: ein Weg für Glocke und Mail, die Art bestimmt die Kanäle

Date: 2026-10-21
Status: Proposed

## Context and Problem Statement

Bis #828 gab es zwei getrennte Wege, jemanden zu benachrichtigen. `NotificationService` legte einen
Eintrag für die Glocke an. Mails verschickten die Aufrufer selbst über `MailService`: der
Budget-Alert neben seinem Glockeneintrag, die Freigabe- und Abnahmeerinnerungen und „Buchungen
freigegeben“ ganz ohne Glocke. Die Einstellung „Benachrichtigungs-E-Mail-Adresse“ versprach
Mails, die bei geteilten Buchungen nie kamen. Abschalten ließ sich keine dieser Mails, und jeder
Aufrufer baute Anrede, Absender und Fehlerbehandlung von Neuem.

Gewünscht ist, je Art einer Benachrichtigung zu wählen, ob sie in die Glocke kommt und ob per Mail.
Die Frage ist, wo der Versand liegt und wie er an Adresse und Wahl der Person kommt:
`notification` darf nur `common` und `auth` importieren
(`ArchitectureTest.notificationShouldAccessCommonAuthOnly`), und `employee`, das die Adresse
speichert, importiert `notification`.

## Considered Options

* **A — Versand in `notification`, Empfänger über eine Schnittstelle in `common`**, die `employee`
  umsetzt; die Arten tragen die Module über einen Contributor bei (das Muster von `common.beta`)
* **B — `notification` darf `settings` importieren** und liest Adresse und Wahl selbst
* **C — jeder Aufrufer verschickt weiter selbst**, mit einer gemeinsamen Hilfsmethode, die die
  Wahl prüft

## Decision Outcome

Chosen: **A**. Jedes Modul deklariert seine Arten als `NotificationKind` (Schlüssel, Platz auf der
Einstellungsseite, Mail an oder aus als Voreinstellung, Mail immer) und liefert sie mit einem
`NotificationKindContributor`. Wer benachrichtigt, ruft
`NotificationService.emitNotification(kind, logins, message)`. Der Service fragt
`NotificationRecipients` (in `common.notification`, umgesetzt von `employee`) nach Vorname,
Adresse, Sprache und Wahl jedes Empfängers. Er legt den Eintrag für die Glocke an, wo die Person ihn
will, und verschickt die Mail, wo sie sie will oder die Art immer gemailt wird.

Eine abschaltbare Mail geht erst nach dem Commit raus, und ein Fehler wird nur geloggt. Eine Mail,
die immer kommt (die Erinnerungen), ist der Zweck der Aktion: Sie geht sofort raus, und ein Fehler
erreicht als `XX-0005` die Person, die sie geschickt hat.

B hätte die Regel aufgeweicht, die `notification` frei von allem außer `common` und `auth` hält, und
`notification` die Ablage einer anderen Einstellung (`employee`) über ihren Schlüssel lesen lassen.
C hätte das Problem nur verteilt: Jede neue Art hätte die Prüfung wieder vergessen können.

### Consequences

* Good: Eine neue Art braucht eine Konstante, ihren Text in beiden Bundles und den Aufruf; Mail,
  Wahl und Einstellungsseite kommen mit. `NotificationKindRegistryTest` prüft die Texte.
* Good: Mails gehen in der Sprache der empfangenden Person raus, mit derselben Anrede, demselben Link
  und demselben Fuß für alle Arten.
* Good: Keine neue Modulkante; `ArchitectureTest` bleibt unverändert.
* Neutral: Die Mail sagt, was die Glocke sagt. Wo sie mehr erzählen soll, trägt `NotificationMessage`
  einen eigenen Text für die Mail (`mailDescriptionKey`); der Budget-Alert nennt dort den Auftrag.
* Bad: Wer keinen Login hat, bekommt nichts mehr. Bis #828 ging die Mail an die Standardadresse der
  Person. Eine Erinnerung, die so niemanden erreicht, meldet das der auslösenden Person.
* Neutral: Die Glocke einer Art, deren Modul sie selbst schaltet (das Achievement mit seinem Popup),
  bleibt dort; das Modul ruft dann für die Mail allein `emitMail`.
