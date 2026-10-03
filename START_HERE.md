# Browser / hosted edition

The connected web edition is now included. Read [WEB_START_HERE.md](WEB_START_HERE.md) for online PostgreSQL, deployment, and browser pages. The original desktop instructions are preserved below.

# Java Quiz Platform — Review 1

A Java Swing desktop application with Admin, Creator and Participant accounts. It uses a real H2 database through JDBC. This is the working desktop stage of the assigned Online Quiz Platform, not an internet-hosted website.

## Start on Windows

1. Extract the entire ZIP to a new folder. Do not run files from inside the ZIP.
2. Open the extracted `QuizPlatform` folder.
3. Double-click `run.bat`. Java 17 or newer must be installed. No Maven or MySQL installation is needed; the database driver is included.
4. On the first launch, create your administrator account. Use your own email and a password of at least eight characters. The email is a local login identifier; the app does not send email.
5. Log in with those details.

If Java is not found, install a JDK 17 or newer, reopen the terminal, and check `java -version`. If an older Java runs, correct PATH so the newer JDK's bin directory comes first. You do not need a coding agent to launch this project.

There are no preset users, sample quizzes or placeholder buttons. Create the data you want to present.

## First complete workflow

1. Log in as Admin. Open **Manage users > Add user**. Create one Creator and one Participant account; remember their passwords.
2. Log out and log in as Creator. Click **Create quiz**.
3. Enter a title, category and duration in seconds. Click **Add question**, enter four options, select the correct option and enter an explanation. Add as many questions as you need.
4. Click **Save draft**. Select the saved quiz and click **Submit for approval**.
5. Log in as Admin. Select the pending quiz and click **Inspect**. The full questions, options, answers and explanations are visible. Click **Approve** or **Reject**. A rejection includes a reason visible to the creator.
6. Log in as Participant. Select the approved quiz and click **Take selected quiz**. Answer questions and click **Submit quiz**, or allow the timer to expire.
7. A detailed report opens. **My results** stores every attempt, including previous attempts. Admins see all results; creators see results for their own quizzes.
8. Close and reopen the app to confirm that accounts, quizzes and results remain saved.

For a quick timer demonstration, make a 10-second quiz. Ordinary quizzes can have durations up to 7200 seconds (two hours). Each correct answer earns one point; wrong or unanswered questions earn zero. Retakes are allowed and stored separately.

## Editing and deletion

- Admin can create, inspect, edit and delete quizzes, including quizzes created by others.
- Creator can manage only their own quizzes.
- Every saved edit returns a quiz to Draft. Submit it again for approval before participants can take it.
- Submitted results retain the original title, questions, options, correct answers and explanations even after later quiz edits.
- A quiz with submitted results cannot be deleted. A user with linked quizzes or results cannot be deleted. These restrictions preserve records; they are not disabled demo actions.
- You cannot delete your currently signed-in admin account or remove its admin role.
- Accounts can also be registered from the login screen as Creator or Participant. Admin accounts are created only by an existing admin after initial setup.
- If saving a finished quiz fails, answers are frozen and the window offers **Retry saving result**. Keep that window open until saving succeeds.

## Where your data lives

The app creates `data/quiz.mv.db` inside the project folder on first launch. Keep the whole extracted project in one place and run only one copy at a time. To back up your work, close the app and copy the entire project folder. The ZIP supplied here contains no user database.

This is a local college project. The H2 file uses a local default database account, while application passwords are salted and hashed. Protect the project folder; the app is not a hosted service or a security boundary against someone who can edit local files.

## Open or change the source

Open the entire `QuizPlatform` folder in VS Code. The source is under `src/quiz`; do not inspect decompiled `.class` files in `build`.

After modifying Java files, run `build.bat`, then `run.bat`. Building needs a full JDK 17 or newer, including `javac` and `jar`. The prebuilt `QuizPlatform.jar` is included so your first run does not require compilation. VS Code Java settings are included for use with its Java extension.

On macOS/Linux, run `sh run.sh`. To rebuild there:

```sh
mkdir -p build
javac --release 17 -encoding UTF-8 -cp 'lib/*' -d build src/quiz/*.java
jar --create --file QuizPlatform.jar --main-class quiz.Main -C build quiz
sh run.sh
```

Use `run.bat` or `run.sh` to launch; they consistently set the working directory. Keep `database/schema.sql` and `lib/h2-2.3.232.jar` beside the application as supplied.

## Scope

Included: GUI, account registration and login, user CRUD, quiz and question CRUD, approval/rejection, role restrictions, category filtering, timed attempts, automatic scoring, attempt history and question-by-question reports, real JDBC persistence and transactions.

Not implemented in this Review 1 version: Servlets/browser access, hosted multi-device use, messaging, reminders, leaderboards, graphical analytics, global system settings, email delivery or manual regrading. Your rubric places Servlet implementation in Review 2. If faculty requires the entire longer product specification in Review 1, this scope must be expanded.

H2 is a relational database accessed using JDBC. Your supplied Review 1 rubric does not name a required database engine. This version uses H2, not MySQL; it cannot use a MySQL server by simply changing the connection URL because the schema also differs.

## Verification

`test.bat` compiles and runs the included backend tests against a separate temporary in-memory database. It does not touch your saved project data. The checks cover the role workflow, input failures, old report preservation, real rollback on a failed answer insert, and competing session finish calls.

See `REVIEW1.md` for the rubric-to-code mapping. See `THIRD_PARTY.txt` for the bundled database dependency.
