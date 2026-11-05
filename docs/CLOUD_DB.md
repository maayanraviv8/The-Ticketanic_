# Google Cloud SQL — Setup & Access Guide

## Connection Details

| Field    | Value                                      |
|----------|--------------------------------------------|
| Host     | 34.134.20.217                              |
| Port     | 5432                                       |
| Database | postgres                                   |
| Username | postgres                                   |
| Password | Ticketanic2026!                            |

---

## Step 1 — Run the App Against the Cloud DB

The app defaults to the cloud profile automatically. Just run it normally:

```bash
mvn spring-boot:run
```

Or in IntelliJ: press the green **Run** button. No extra configuration needed.

---

## Step 2 — Verify Connection

On startup you should see these log lines:

```
DB connectivity check PASSED.
DB table check PASSED — all 7 expected tables present.
DB startup verification PASSED — all checks green.
```

If you see `DB connectivity check FAILED` — check your network connection or contact the team.

---

## Switching to In-Memory Mode (no DB needed)

For local development or running tests without a database connection:

Set the environment variable:
```
TICKETANIC_PROFILE=dev
```

In IntelliJ: Run → Edit Configurations → Environment variables → add `TICKETANIC_PROFILE=dev`

This switches to an in-memory H2 database. No cloud connection, no IP authorization needed.

---

## Running Tests

Tests always use in-memory H2 regardless of profile — no setup needed:

```bash
mvn test
```
