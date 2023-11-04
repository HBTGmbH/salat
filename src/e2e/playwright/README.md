# e2e-Test
## Vorbereitung
* `$ npm install`
* DB Starten: `$ docker-compose -f .\docker-compose-infra.yml up` (im Project root)
* Salat mit Profil 'test' starten (die Authentifizierung wird deaktiviert)
    * [runConfiguration](../../.run/SalatApplication test.run.xml)

## Ausführung
* `$ npx playwright test`

## Entwicklung von Tests
* `$ npx playwright test --ui`
    * man kann einzelne tests watchen oder ganze Dateien
 