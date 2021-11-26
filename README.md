### Environment variables

The following environment variables must be set in each environment/stage:

```
DATABASE_USERNAME
DATABASE_PASSWORD
DATABASE_HOST
DATABASE_NAME
SALAT_URL
SALAT_JIRA_CONSUMER_KEY
SALAT_JIRA_CONSUMER_PRIVATE_KEY
SALAT_MAIL_HOST
```

The following is an example for local testing with the included docker-compose file:

```
DATABASE_USERNAME=salattest
DATABASE_PASSWORD=salattest
DATABASE_HOST=localhost
DATABASE_NAME=salat
SALAT_URL=localhost
SALAT_JIRA_CONSUMER_KEY=x
SALAT_JIRA_CONSUMER_PRIVATE_KEY=x
SALAT_MAIL_HOST=localhost
```

## Run locally

Steps to start Salat locally:

1. Build the .war-file: `./mvnw -D"maven.test.skip=true" package`
2. Run docker-compose: `docker-compose up`
3. Open in browser: http://localhost:8080/tb
   1. Valid credentials in the test-dataset are: admin/admin, bm/bm, tt/tt

Shutdown:
1. Stop docker-compose: CTRL+C
2. Remove built containers: `docker-compose down`

### Troubleshooting

Clean rebuild of compose containers: `docker-compose build --no-cache`