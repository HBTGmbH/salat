# How to test the new Thymeleaf + Tabler UI

This guide explains how to run the application locally and validate the new Scheduled Report Jobs page implemented with Spring MVC + Thymeleaf + Bootstrap 5 + Tabler.

---

## Prerequisites
- Java 21
- Docker and docker compose (for local MySQL)
- Maven (or use the included mvnw wrapper)

The project is packaged as a Spring Boot WAR and can be run via Docker Compose or directly from Maven.

---

## Option A: Run everything with Docker Compose (recommended)
1. Build the image:
   - `./mvnw spring-boot:build-image`
2. Start infrastructure and the app:
   - `docker compose up -d` (or `docker-compose up -d` on older Docker)
3. Open the app:
   - http://localhost:8080?login-name=tt

Use the `login-name` parameter to simulate a login (see README for available signs like `admin`, `bm`, `tt`).

---

## Option B: Run app locally against Dockerized MySQL
1. Start only the DB:
   - `docker compose -f docker-compose-infra.yml up -d`
2. Ensure your environment variables are set (see README):
   - `SPRING_DATASOURCE_USERNAME=salattest`
   - `SPRING_DATASOURCE_PASSWORD=salattest`
   - `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/salat?useUnicode=true&useJDBCCompliantTimezoneShift=true&serverTimezone=Europe/Berlin&useLegacyDatetimeCode=false&autoReconnect=true`
3. Start the app from the project root:
   - `./mvnw spring-boot:run`
4. Open the app:
   - http://localhost:8080?login-name=tt

---

## Navigate to the new UI
- URL: http://localhost:8080/reporting/jobs2
  - You can append `?login-name=admin` (or another valid sign) to switch the current user at any time, e.g.:
  - http://localhost:8080/reporting/jobs2?login-name=admin

This page lists Scheduled Report Jobs using the new stack.

---

## Seeding/Preparing data (if the list is empty)
If you do not see any scheduled jobs yet, you can create some using the existing legacy pages:
- Open the legacy reports area and create a scheduled job, then refresh `/reporting/jobs2`.
  - Legacy entry points exist under legacy Struts/JSP routes such as `/do/ShowReports` or reporting management pages.

Alternatively, import or create data directly in the database if you are comfortable doing so.

---

## What to verify
- Page renders without errors.
- Tabler/Bootstrap styles load:
  - CSS comes from WebJars; base layout also includes a CDN fallback for Tabler.
- Table shows job rows with:
  - Name, Report, Recipients, Enabled flag (green check/red cross), Cron expression and a human-readable description, Last Updated, Edit/Delete actions.
- “Create New Scheduled Job” button links to the existing creation flow.

---

## Troubleshooting
- If styles are missing:
  - Check that WebJars are available at `/webjars/...` and that your browser can load `tabler.min.css` either from WebJars or the CDN (base layout includes a fallback).
- If you see an error about `BuildProperties`:
  - Run `./mvnw package` once (see README troubleshooting section) and restart.
- If authentication blocks you:
  - Use the `?login-name=<sign>` parameter to switch the current user for local testing (see README for known signs).
- If you get a DB connection error:
  - Ensure Dockerized MySQL is running and environment variables are configured as in the README.

---

## Notes
- This UI is part of the migration from Struts/JSP to Spring MVC + Thymeleaf per AGENTS.md.
- Legacy pages remain available so you can create/edit data while we migrate screens incrementally.
