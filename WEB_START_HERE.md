# Java Quiz Platform — web edition

This upgrades QuizPlatform_Review1 into a browser application while preserving the
Java desktop sources and their role workflows. The browser calls a Java HTTP API;
Java handles authentication, permissions, quiz approval, scoring, and SQL persistence.

## Connected pages

- Login and registration (Creator or Participant).
- Role-specific overview and quiz collection.
- Top-right account avatar: name, email, actual signed-in role, account page, logout.
- Admin user CRUD and quiz inspection/approval/rejection.
- Creator quiz/question editor, drafts, and submission for approval.
- Participant timed attempts with saved answers and refresh/resume support.
- Results and question-by-question reports with explanations.

Roles come from authenticated SQL records. There is no role-preview dropdown.
Question answer keys are not returned by participant quiz or active-attempt APIs.
Historical reports keep their own question snapshots when quizzes are edited.

## One public link

Publish the Java web service and attach an online PostgreSQL database. The same
HTTPS URL serves the frontend and API. A tutor opens that URL, signs in, and uses
the app. No Java installation, terminal, ZIP, or running student laptop is needed.

Production mode refuses to start without PostgreSQL configuration. Accounts,
quizzes, saved answers, deadlines, login sessions, and results live in SQL. There
is no browser localStorage database and no production fallback to a local file.

Deployment has NOT been performed in this deliverable. It requires access to
your hosting account and PostgreSQL service. No public URL has been provisioned.

## Hosting settings

A Dockerfile and Render service blueprint are included. Use the project root
(the folder containing Dockerfile) as the deployment root. The web service can
also run on another Java/Docker host with the same environment variables.

| Setting | Value |
| --- | --- |
| QUIZ_PRODUCTION | true |
| DATABASE_URL | The hosted PostgreSQL connection URL, stored as a secret |
| QUIZ_SETUP_TOKEN | A random secret of at least 24 characters |
| PUBLIC_URL | Exact HTTPS origin, without a path; optional on Render because RENDER_EXTERNAL_URL is supplied |
| PORT | Assigned by the host, or 8080 |

Use the database provider's encrypted connection URL. The app adds
sslmode=require if the URL does not specify an SSL mode. Database connection
secrets are read only on the Java server. Never put them in web/app.js.

After deployment, open the site and create the first administrator using the
setup key from the hosting settings. Initial setup closes after the first user
is created. Register Creator and Participant accounts, or create them from the
admin's Manage users page. The app does not send email or verify email ownership.

The Render blueprint creates a FREE web service and asks for an EXISTING
PostgreSQL URL. It does not create or purchase a database. Free web compute can
sleep when unused, so the first request may take longer. Review database retention,
backups, quotas, and pricing before selecting a database plan. Do not treat a
short-lived trial database as permanent storage.

## Existing laptop records

The supplied Review 1 ZIP contains no data/quiz.mv.db database. Your laptop's
existing accounts and quizzes are therefore not included. They are not silently
re-created or copied into the web database. To migrate those records, first close
the desktop app and back up its data/quiz.mv.db; the data migration must be done
against that actual file and an empty destination database. Keep database files
and secrets out of a public Git repository.

## Local development (optional)

Double-click run-web.bat on Windows, or run sh run-web.sh on Linux/macOS with
JDK 17+. Open http://localhost:8080. Without production/database variables, this
uses the original local H2 data/quiz file. Do not run the desktop and web editions
against the same H2 file at the same time.

The first local start prints a setup key. If your existing local database already
contains accounts, use those accounts; setup is not shown.

VS Code Live Server cannot run Java or host the online database. Test this full
application through the Java server. The frontend files use API routes on the same
origin, so opening index.html directly is not the full app.

## Implementation guide

- src/quiz/WebServer.java: HTTP routes, sessions, CSRF, account API, static files.
- src/quiz/WebStore.java: durable timed attempts, safe JSON views, result reports.
- src/quiz/Database.java: PostgreSQL hosting and H2 local connections.
- src/quiz/Json.java: small dependency-free JSON codec.
- Existing UserDAO, QuizDAO, ResultDAO: reused Java/JDBC business operations.
- database/web-schema.sql: sessions and active-attempt tables.
- web/index.html, style.css, app.js: connected browser pages and account menu.

The scheduled Java executor submits expired attempts. Deadlines and answer state
are stored in SQL, so restart recovery uses the original deadline. The result and
completed-attempt reference commit in one transaction; repeated submit requests
return the same result. Browser refresh restores the attempt from its URL.

The code uses Java's built-in HTTP server, not Servlets. It does not claim to
complete any separate Servlet requirement in a later academic review.

## Verification performed

- Compiled all Java sources with Java 17.
- Passed the original project's 39 backend checks.
- HTTP integration checks: registration/login, role checks, user CRUD, quiz CRUD,
  rejection/approval, hidden answer keys, per-user result access, saved answers,
  simultaneous submissions, historical result snapshots, CSRF, expiry, logout/login.
- Frontend DOM integration checks with the real Java API: login, avatar menu,
  account creation, search, quiz creation/approval, participant navigation,
  answer saving, submission, and result page.

The integration database was isolated H2. A live PostgreSQL connection, deployed
host, and visual browser rendering still require verification in the target
hosting environment. These have not been claimed as completed.
