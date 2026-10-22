# ADR-0043 Was ein Achievement belohnt: eigenes Verhalten, ehrlich gebucht, für alle erreichbar

Date: 2026-10-22
Status: Proposed

## Context and Problem Statement

Achievements sollen das Buchen spielerisch aufwerten (#837). Den Grundsatz dafür hat #837 schon
gesetzt: nur positive Anreize, keine Rangliste, niemand sieht die Achievements eines anderen. Wie
sie gebaut werden, regelt ADR-0040.

Mit den ersten Achievements (#1501, #1512) kommen Ideen für weitere. Darunter sind solche, die
naheliegen und trotzdem schaden: eine Medaille für wenige Krankheitstage oder für einen hohen Anteil
fakturierbarer Stunden. Keine Rangliste allein schließt sie nicht aus. Die Frage ist, was ein
Achievement überhaupt belohnen darf, und zwar so, dass sie nicht bei jeder neuen Idee neu
entschieden werden muss.

## Considered Options

* **A — alles, was Salat messen kann**, solange es keine Rangliste gibt
* **B — Ergebnisse, die dem Unternehmen nützen**, als persönliche Medaille: Fakturierung,
  Anwesenheit, Umsatz
* **C — nur eigenes Verhalten, das die Buchung ehrlich lässt**, geprüft mit fünf Fragen

## Decision Outcome

Chosen: **C**. Ein Achievement kommt nur, wenn es alle fünf Fragen besteht:

1. **Steuert die Person es selbst?** Belohnt wird, was sie tut, nicht das Ergebnis von Besetzung,
   Rolle oder Projekt.
2. **Bleibt die Buchung ehrlich?** Es darf keinen Anreiz geben, Dauer, Auftrag oder Tag anders zu
   buchen als gearbeitet. Rechnungen und Controlling hängen daran.
3. **Kommt es ohne sensible Daten aus?** Keine Gesundheitsdaten, keine Leistungs- oder Umsatzzahlen
   der Person.
4. **Kann jede Person es erreichen**, unabhängig von Gesundheit, Rolle, Teilzeit und Projekt? Die
   Profilseite zeigt jeder Person, was ihr noch fehlt. Ein Achievement, das sie nie erreichen kann,
   steht dort als Makel.
5. **Tut es der Person gut, es anzustreben?** Keine Belohnung für mehr Arbeit (Überstunden,
   Wochenende, Nacht, Feiertag) und keine, die zum Arbeiten trotz Krankheit verleitet.

Damit fallen die naheliegenden Ideen weg:

* **Wenige Krankheitstage** fallen bei den Fragen 1, 3, 4 und 5 durch. Wer seine Serie halten will,
  arbeitet krank. Wer chronisch krank ist oder eine Behinderung hat, erreicht die Medaille nie.
  Krankheitstage sind Gesundheitsdaten (Art. 9 DSGVO); erfasst werden sie für Abwesenheit und
  Entgeltfortzahlung, nicht für eine Auszeichnung.
* **Viel fakturiert** fällt bei den Fragen 1 bis 4 durch. Fakturierbarkeit hängt an der Besetzung:
  Wer intern arbeitet, im Backoffice, in der Akquise oder in der Fortbildung, bekommt die Medaille
  nie. Die Belohnung zieht Stunden auf fakturierbare Aufträge, und sie ist eine Leistungskennzahl
  der Person.
* **Überstunden, Wochenend-, Nacht- und Feiertagsarbeit** fallen bei Frage 5 durch.
* **Bestimmte Dauern** („genau 3:14 h am Pi-Tag“) fallen bei Frage 2 durch.

Die Absicht dahinter lässt sich oft anders bedienen. Gesundheit über Erholung, die die Person selbst
steuert: Urlaub genommen, Pausen eingehalten. Fakturierung über Sorgfalt, die die Rechnung leichter
macht: aussagekräftige Beschreibungen, Ticket-Bezug, pünktliche Freigabe (#1502).

A scheidet aus, weil „keine Rangliste“ den Vergleich nicht verhindert: Die Profilseite zeigt jeder
Person, was ihr fehlt, und wer seine Achievements teilt, macht sie vergleichbar. B scheidet aus,
weil ein Anreiz auf Ergebnisse in Vergütung und Zielvereinbarung gehört, wo er verhandelt wird,
und nicht in ein Spiel, das freiwillig Spaß machen soll.

### Consequences

* Good: Eine neue Idee wird an den fünf Fragen gemessen, statt jedes Mal neu diskutiert zu werden.
* Good: Jedes Achievement ist für alle erreichbar und verleitet nicht dazu, Buchungen zu schönen.
* Bad: Was dem Unternehmen am meisten nützt, Fakturierung und Umsatz, bleibt bewusst außen vor.
* Neutral: Die Fragen prüft das Review, kein Test. Sie stehen als Regel in
  [`docs/guides/domain-rules.md`](../guides/domain-rules.md#achievements-was-belohnt-wird).
