### Environment variables

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
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/salat?useUnicode=true&useJDBCCompliantTimezoneShift=true&serverTimezone=Europe/Berlin&useLegacyDatetimeCode=false
```

## Run locally

Steps to start Salat locally:

1. Build the .war-file: `./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=docker.io/hbt/salat:latest`
2. Run docker-compose: `docker-compose up`
3. Open in browser: http://localhost:8080
4. Valid credentials in the test-dataset are: admin/admin, bm/bm, tt/tt

Shutdown:
1. Stop docker-compose: CTRL+C
2. Remove built containers: `docker-compose down`

### Debugging
Start local database only, without Salat:
`docker-compose -f docker-compose-infra up`

! Be sure to remove existing docker containers before by running `docker-compose down` if you had the application running before.


### Troubleshooting
