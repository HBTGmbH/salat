### Environment variables

The following environment variables must be set in each environment/stage:

```
DATABASE_USERNAME
DATABASE_PASSWORD
DATABASE_HOST
DATABASE_NAME
SALAT_URL
SALAT_MAIL_HOST
```

The following is an example for local testing with the included docker-compose file:

```
DATABASE_USERNAME=salattest
DATABASE_PASSWORD=salattest
DATABASE_HOST=localhost
DATABASE_NAME=salat
SALAT_URL=localhost
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

### Debugging
Start local database only, without Salat:
`docker-compose -f docker-compose-infra up`

! Be sure to remove existing docker containers before by running `docker-compose down` if you had the application running before.


### Troubleshooting

Clean rebuild of compose containers: `docker-compose build --no-cache`

### DB Migrations
drop table referenceday_timereport;
drop table worklog;
drop table worklogmemory;
drop table ticket_timereport;
alter table timereport drop foreign key FKDA2C766119CE4732;
alter table timereport drop column TICKET_ID;
drop table ticket;
alter table employee drop column jira_oauthtoken;
drop table monthlyreport;



