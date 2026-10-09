# Review 1 checklist

The supplied rubric is headed **Java GUI Based Projects Marking Rubric 1**. This project therefore includes a Swing GUI in addition to the six marked requirements. The mapping identifies implemented code; it does not guarantee an evaluator's marks.

| Requirement | Marks | Where it is implemented | How to demonstrate it |
| --- | ---: | --- | --- |
| OOP: polymorphism, inheritance, exception handling, interfaces | 10 | `User.java`: Admin, QuizCreator and Participant extend User and override `getRole()` and `getPermissions()`. `WebStore.user(User u)` and Dashboard call permissions through a User reference. `QuizDAO.load()` throws `QuizNotFoundException`; WebServer and `UI.button` catch it explicitly. `DatabaseOperations<T>` extends `Repository<T>` with CRUD methods; QuizDAO implements it. WebServer, Dashboard and QuizEditor call through `DatabaseOperations<Quiz>` references. | Log in under different roles and open My account. Request a deleted quiz to see HTTP 404 or a desktop warning. Show the interface reference in QuizEditor and the overridden methods in QuizDAO. |
| Collections and generics | 6 | `DataRepository<T>` uses `HashMap<Integer,T>` for desktop user and web result lookups. Question uses `HashSet<String>` for unique answer options. Quiz uses `List<Question>`, Dashboard uses a category Set, and QuizSession uses an answer Map. DAO interfaces also use generic type parameters. | Select an account by its ID and open a permitted result. Try answer options that differ only in case or whitespace; validation rejects them. Show `DataRepository<User>` and `DataRepository<Attempt>`. |
| Multithreading and synchronization | 4 | QuizWindow starts a countdown Thread. QuizSession has synchronized state methods. `QuizMaintenanceService implements Runnable` runs every second on WebServer's scheduled executor. Synchronized WebStore methods and SQL row locks coordinate expiry and manual submission. Swing updates use `SwingUtilities.invokeLater`. | Start a 10-second web quiz, leave the quiz page, then view the automatically saved result. ProjectTest races background expiry and manual submission and verifies exactly one result. |
| Model classes and database operations | 7 | User, Quiz, Question and Attempt hold project data. UserDAO, QuizDAO and ResultDAO contain database operations. DatabaseConnection opens connections; Database initializes tables. | Show `database/schema.sql`, a model, its DAO and the shared connection factory. |
| JDBC, CRUD, PreparedStatement | 3 | `DatabaseConnection.getConnection()` uses DriverManager and Connection with the bundled PostgreSQL/H2 drivers. DAOs use PreparedStatement and ResultSet. Users and quizzes support create, read, update and delete, subject to record-preservation rules. | Create/edit/delete an unused user or quiz. Show the prepared SQL and CRUD interface calls. Restart the app and show saved records. |
| JDBC transaction management | 3 | ResultDAO.save atomically inserts an attempt and its answers. QuizDAO.save atomically saves quiz details and questions. WebStore.start and submission also use `setAutoCommit(false)`, `commit()` and explicit `rollback()` in catch blocks. | Run ProjectTest: an invalid second answer leaves no partial attempt; an invalid second question restores the original quiz title and questions. |

## File map

| File | Responsibility |
| --- | --- |
| Main.java | Application entry point |
| LoginWindow.java | Login, registration and first-admin setup |
| Dashboard.java | Role dashboards, user management, quiz management and result tables |
| QuizEditor.java | Edit quiz details and questions |
| QuizWindow.java | Answer questions, countdown and result submission |
| UI.java | Shared small Swing helpers and error display |
| User.java | Common user data and role/permission behavior for the three subclasses |
| Quiz.java | Quiz data and question list |
| Question.java | Question, four options, correct option and explanation |
| Attempt.java | A saved attempt summary |
| Repository.java | Generic read interface implemented by DAOs |
| DatabaseOperations.java | Generic CRUD interface implemented by QuizDAO and used by app callers |
| DataRepository.java | Generic HashMap index for refreshed records by ID |
| UserDAO.java | Account CRUD and login |
| QuizDAO.java | Quiz persistence and approval workflow |
| QuizNotFoundException.java | Custom checked exception for missing or deleted quizzes |
| ResultDAO.java | Transactional submissions and reports |
| Database.java | Table initialization and existing connection helper |
| DatabaseConnection.java | Shared DriverManager connection factory for PostgreSQL and H2 |
| QuizSession.java | Synchronized in-progress answers and timing state |
| QuizMaintenanceService.java | Runnable background service for expired web attempts |
| Passwords.java | Salted password hashing and verification |
| WebServer.java | HTTP routes, sessions, interface-based CRUD and scheduled background tasks |
| WebStore.java | Web attempts, transaction locking, safe quiz views and generic result lookups |
| Json.java | Lightweight JSON encoding and decoding |

There are 25 application Java files. User.java contains its three small role subclasses alongside the base class. All source uses one `quiz` package, ordinary loops, explicit JDBC and small data classes. There is no Spring, ORM or web framework. Swing event callbacks use short Java lambdas.

## Presentation route

1. State the project purpose and roles.
2. Creator creates a short quiz and submits it.
3. Admin inspects its full contents and approves it.
4. Participant takes it; show timer and report.
5. Reopen saved results and explain the database tables.
6. Point to the exact code locations in the table above.

Keep an already-approved quiz in your own database as a backup for the presentation. Do not rely only on a live creation flow. Build your presentation data yourself after extracting the project.

This version prioritizes a working Review 1 core. It is still a real multi-class project: OOP, JDBC and concurrency will require study. The GUI and password utility can be read after the model classes and DAOs. No claim is made that all required concepts are understandable before learning Java.

## Checks completed for this build

- Compiled the application for Java 17.
- All 69 included backend checks passed, including permissions through User references, custom exceptions, named JDBC connections, interface CRUD, generic ID lookup, HashSet validation, transaction rollback, and simultaneous background/manual submission.
- 40 local HTTP checks passed, covering role permissions, missing quizzes, user and quiz CRUD, approval, result access, and automatic expiry without an open attempt page.
- Account-page markup checks confirmed each role's permissions render correctly and permission text is escaped.
- A separate file-backed persistence check saved accounts, an approved quiz and a result, then reopened them successfully in a new JVM.
- Swing GUI checks opened Admin's Inspect view, added a question and saved a new draft through the editor, selected an answer, and verified that timer expiry saved exactly one correctly scored result and opened its report.
- Screenshots of the dashboard, inspection view, quiz window, editor and report were visually checked for layout issues.
- These checks ran on Linux with Java 17. The Windows launcher is supplied but was not executed on Windows here.
