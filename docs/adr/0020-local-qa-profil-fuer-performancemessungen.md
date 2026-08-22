# ADR-0020 Profil `local-qa` für Performancemessungen

Date: 2026-08-22
Status: Proposed

## Context and Problem Statement

Antwortzeitprobleme werden lokal reproduziert — meist mit einem Abzug der Produktionsdaten
in der lokalen MySQL. Das Ergebnis solcher Messungen war bisher nicht auf Produktion
übertragbar, weil das Profil `local` und das Profil `production` in genau den
Einstellungen auseinanderlaufen, die die Antwortzeit bestimmen:

| Einstellung | `local` | `production` |
|---|---|---|
| `spring.web.resources.cache.period` | *(nicht gesetzt)* | `1d` |
| `server.tomcat.resource.allow-caching` / `cache-ttl` | *(nicht gesetzt)* | `true` / `1d` |
| `salat.auth-service.cache-expiry` | `1s` | `1h` |
| devtools auf dem Classpath | ja | nein |

Es gibt keine `application-local.yaml`; `local` läuft auf der Basiskonfiguration.
Dadurch werden lokal alle Webjars (plotly, tabler, bootstrap, jquery, htmx, tom-select)
ohne Cache-Header und ohne Tomcat-Resource-Cache bei jedem Seitenaufruf neu ausgeliefert,
devtools deaktiviert zusätzlich den Thymeleaf-Template-Cache, und der Autorisierungs-Cache
in `AuthService.ensureUpToDateCache()` wird im Sekundentakt statt stündlich neu aufgebaut.

Beide Fehlerrichtungen sind schädlich: Lokal sichtbare Langsamkeit existiert in Produktion
teilweise gar nicht (Fehlalarm), und umgekehrt wird echter Produktionsaufwand von lokalem
Rauschen überdeckt (blinder Fleck).

## Considered Options

* Beim Messen die Einstellungen ad hoc per JVM-Argumenten überschreiben
* `application-local.yaml` anlegen und `local` selbst produktionsnah machen
* Eigenes Profil `local-qa`, das `local` per Profilgruppe mitzieht und nur die
  performancerelevanten Produktionswerte überschreibt

## Decision Outcome

Chosen: **eigenes Profil `local-qa`**, weil ad-hoc-JVM-Argumente nicht reproduzierbar sind
und nicht reviewt werden, und weil ein produktionsnahes `local` die tägliche Entwicklung
verschlechtern würde (kein Template-Reload, gecachte Assets).

`local-qa` wird über eine Profilgruppe in `application.yaml` aktiviert und zieht `local` mit.
Dev-Login und lokale Datasource bleiben damit unverändert; überschrieben wird ausschließlich,
was die Antwortzeit beeinflusst.

Aktivierung: `-Dspring.profiles.active=local-qa`

### Parity-Regel

Jeder Eintrag in `application-local-qa.yaml` unter `server`, `spring` und `salat` **spiegelt
exakt** `application-production.yaml`. Werte dort werden nicht isoliert getunt. Ändert sich
eine performancerelevante Einstellung in Produktion, wird `application-local-qa.yaml`
im selben PR mitgezogen.

Zwei bewusste Ausnahmen, beide im Profil kommentiert:

1. **Actuator `metrics`** ist zusätzlich exponiert. Das ist das Messinstrument
   (`http.server.requests` pro URI mit Perzentilen). Produktion exponiert nur `health` —
   eine Entscheidung über die Angriffsfläche, keine Performance-Einstellung; die Timer
   selbst laufen in Produktion ebenfalls mit.
2. **Azure Easy Auth / OAuth2** wird nicht gespiegelt, weil es lokal keinen Azure-Login gibt.
   Die für die Antwortzeit relevante Eigenschaft des Auth-Pfads ist aber in beiden Profilen
   dieselbe: `SessionCreationPolicy.STATELESS`, also Re-Authentifizierung samt
   `AuthenticationSuccessEvent`-Kaskade bei jedem Request.

### Was `local-qa` nicht abdeckt

* **Zweite Cache-Ebene**: `hibernate.cache.use_second_level_cache` ist in beiden Profilen
  `false` — bewusst nicht in `local-qa` eingeschaltet, sonst wäre lokal schneller als Produktion.
* **JVM-Warmlauf**: die ersten Requests messen JIT, nicht die Anwendung.
* **MySQL**: der lokale Container (`testdb`, `mysql:8`) läuft mit Defaults, u. a.
  `innodb_buffer_pool_size=128M`; Produktion läuft mit `536870912` (512 MB). Mit einem
  Produktionsdatenabzug ist das der dominierende lokale Verfälschungsfaktor. Die Parity-Regel
  gilt hier sinngemäß: für Messläufe den Produktionswert setzen, nicht mehr — sonst ist lokal
  schneller als Produktion. Siehe README.
* **devtools-Restart-Classloader**: `spring.devtools.restart.enabled` wird ausgewertet, bevor
  Config-Dateien geladen sind, und muss als JVM-Argument gesetzt werden. Siehe README.

### Consequences

* Good: lokal gemessene Antwortzeiten sind auf Produktion übertragbar
* Good: die Abweichungen zwischen lokal und Produktion stehen an einer Stelle und sind reviewbar
* Good: `local` bleibt für die tägliche Entwicklung unangetastet (Template-Reload, kein Asset-Cache)
* Bad: eine weitere Datei, die bei Produktionsänderungen mitgepflegt werden muss — die
  Parity-Regel oben ist der Gegenmechanismus
* Neutral: `local-qa` ersetzt keine Lastmessung; es macht Einzelrequest-Messungen belastbar
