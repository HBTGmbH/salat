## Run locally

Requirements:

- Java 25
- Docker (running)
- docker-compose or docker compose

Steps to start Salat locally:

1. Build the image: `./mvnw spring-boot:build-image`
2. Run docker-compose: `docker-compose up -d` (in newer docker versions use `docker compose up -d`)
3. That's it. Salat should now be running. To check, open in browser: <http://localhost:8080?login-name=tt>

Shutdown:
1. Stop docker-compose: CTRL+C
2. Stop built containers: `docker-compose stop` (in newer docker versions use `docker compose stop`)
3. If you want to remove the containers: `docker-compose down` (in newer docker versions use `docker compose down`)

### Login
Open the URL <http://localhost:8080?login-name=<sign>>

You can change the `login-name` parameter to login as a different user.
It is even possible to append `?login-name=<sign>` to any URL, to log in the user.

Valid login-names in the test-dataset are:
  1. **admin**: Administrator
  2. **bm**: "Bossy Bossmann", Administrator
  3. **tt**: "Testy Testmann", Employee

### Logout
There is no need to logout. But you can just click the button to logout.

**With the above login url, you can change the login user at any time without logging out!**

### Debugging
To start only the local database, without the Salat application:
`docker-compose -f docker-compose-infra.yml up`

⚠️ Be sure to remove existing docker containers before by running `docker-compose down` if you had the application running before.


### Measuring response times locally (`local-qa` profile)

The `local` profile runs on the base configuration, which differs from production in exactly
the settings that determine response time (asset caching, Thymeleaf template cache, the
authorization rule cache expiry). Response times measured under `local` are therefore not
transferable to production.

Use the `local-qa` profile instead. It keeps the local dev login and the local datasource,
and overrides only the performance-relevant settings with their production values
(see [ADR-0020](docs/adr/0020-local-qa-profil-fuer-performancemessungen.md)):

    ./mvnw spring-boot:run -Dspring-boot.run.profiles=local-qa \
      -Dspring-boot.run.jvmArguments="-Dspring.devtools.restart.enabled=false"

`spring.devtools.restart.enabled` has to be passed as a JVM argument — it is evaluated before
the configuration files are read, so setting it in a profile has no effect. All other devtools
property defaults are switched off by the profile itself.

Per-endpoint timings are then available at

    http://localhost:8080/actuator/metrics/http.server.requests?tag=uri:/dailyreport/dashboard&login-name=<sign>

The endpoint sits behind the authenticated filter chain, so it needs a valid `login-name`
(or the `salat_dev_login` cookie) just like any page. It reports `COUNT`, `TOTAL_TIME` and
`MAX`; the mean is `TOTAL_TIME / COUNT`. Percentiles are configured as histogram buckets and
become readable once a scraping registry (e.g. Prometheus) is added — the `/actuator/metrics`
endpoint itself does not render them.

Two things `local-qa` cannot fix, which matter a lot when you have imported a production
data dump into your local MySQL:

- **InnoDB buffer pool.** The `testdb` container runs `mysql:8` with defaults, i.e.
  `innodb_buffer_pool_size=128M`, while production runs `536870912` (512 MB). With a
  production-sized dataset the local default alone dominates every measurement. For
  measurement runs, start the db container with the production value — **not more**, or local
  becomes faster than production and the parity rule is broken:

      # docker-compose-infra.yml, service db:
      #   command: --lower_case_table_names=1 --innodb-buffer-pool-size=512M

- **JVM warm-up.** The first requests measure JIT compilation, not the application. Discard
  them and repeat the request a dozen times before reading the percentiles.

### Troubleshooting

#### Missing bean buildProperties
Should you encounter the error

    No bean named 'buildProperties' available

This can be fixed by running

    ./mvnw spring-boot:build-info

Explanation: Maven creates a file named `target/classes/META-INF/build-info.properties`.
This file might be missing when an IDE does not create it. (Happened to Klaus with IntelliJ IDEA, despite Maven Integration).
(by Klaus)

#### Missing bean gitProperties
Should you encounter the error

    No bean named 'gitProperties' available

This can be fixed by running

    ./mvnw git-commit-id:revision

Explanation: Maven creates a file named `target/classes/git.properties`.
This file might be missing when an IDE does not create it. (Happened to Klaus with IntelliJ IDEA, despite Maven Integration).
(by Klaus)

#### DB-Changes
Changes to the database are collected via Liquibase in the following file:

    src/main/resources/db/changelog/db.changelog-master.yaml

## Environment variables

The following environment variables must be set in each environment/stage:

```
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_DATASOURCE_URL
```

The following is an example for local testing with the included docker-compose file:

```
SPRING_DATASOURCE_USERNAME=salattest
SPRING_DATASOURCE_PASSWORD=salattest
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/salat?useUnicode=true&useJDBCCompliantTimezoneShift=true&serverTimezone=Europe/Berlin&useLegacyDatetimeCode=false&autoReconnect=true
```

## AGENTS.md

More detailed design decisions can be found in AGENTS.md