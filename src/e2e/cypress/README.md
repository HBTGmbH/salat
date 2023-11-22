# e2e-Test
## Vorbereitung
* `$ npm install`
* DB Starten: `$ docker-compose -f .\docker-compose-infra.yml up` (im Project root)
* Salat mit Profil 'e2etest' starten (die Authentifizierung wird deaktiviert)
  * [runConfiguration](../../.run/SalatApplication test.run.xml)

## Ausführung
* `$ cypress run`

## Entwicklung von Tests
* `$ cypress open`
  * -> E2E Testing
  * -> Browser auswählen
  * -> spec auswählen
 