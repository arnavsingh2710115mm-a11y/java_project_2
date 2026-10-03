# Review 1 checklist

The supplied rubric is headed **Java GUI Based Projects Marking Rubric 1**. This project therefore includes a Swing GUI in addition to the six marked requirements. The mapping identifies implemented code; it does not guarantee an evaluator's marks.

| Requirement | Marks | Where it is implemented | How to demonstrate it |
| --- | ---: | --- | --- |
| OOP: polymorphism, inheritance, exception handling, interfaces | 10 | `User.java`: Admin, QuizCreator and Participant extend User and override `getRole()`. `Repository<T>` is implemented by the DAO classes. `UI.button` catches operation exceptions; DAOs use checked SQL exceptions and input validation. | Log in under different roles. Open User.java and the Repository implementations. Trigger an invalid input or duplicate email. |
| Collections and generics | 6 | `List<Question>` in Quiz; lists of users/quizzes/results in DAOs; `Set<String>` in Dashboard removes duplicate categories; `Map<Integer,Integer>` in QuizSession records answers; `Repository<T>` is a custom generic interface. | Add multiple quizzes in the same category and inspect the category filter. Show the quiz question list and session answers. |
| Multithreading and synchronization | 4 | QuizWindow starts the `quiz-countdown` Thread. QuizSession's synchronized methods coordinate timer expiry and answer/submission state. Swing UI changes are dispatched with `SwingUtilities.invokeLater`. | Take a 10-second quiz, observe the countdown and automatic submission. The backend test also races two finish calls. |
| Model classes and database operations | 7 | User, Quiz, Question and Attempt hold project data. UserDAO, QuizDAO and ResultDAO contain database operations. Database creates connections and initializes tables. | Show `database/schema.sql`, a model, then its DAO. |
| JDBC, CRUD, PreparedStatement | 3 | Database uses DriverManager. DAOs use Connection, PreparedStatement and ResultSet. Users and quizzes support create, read, update and delete, subject to record-preservation rules. | Create/edit/delete an unused user or quiz. Restart the app and show saved records. |
| JDBC transaction management | 3 | ResultDAO.save inserts the attempt plus all answer rows between `setAutoCommit(false)` and `commit()`. Failure calls `rollback()`. QuizDAO.save also atomically saves quiz details and its questions. | Open ResultDAO.save. Run ProjectTest: an invalid second answer causes a real SQL constraint failure; the test verifies that neither the attempt nor its first answer remains. |

## File map

| File | Responsibility |
| --- | --- |
| Main.java | Application entry point |
| LoginWindow.java | Login, registration and first-admin setup |
| Dashboard.java | Role dashboards, user management, quiz management and result tables |
| QuizEditor.java | Edit quiz details and questions |
| QuizWindow.java | Answer questions, countdown and result submission |
| UI.java | Shared small Swing helpers and error display |
| User.java | Common user data and the three role subclasses |
| Quiz.java | Quiz data and question list |
| Question.java | Question, four options, correct option and explanation |
| Attempt.java | A saved attempt summary |
| Repository.java | Generic read interface implemented by DAOs |
| UserDAO.java | Account CRUD and login |
| QuizDAO.java | Quiz persistence and approval workflow |
| ResultDAO.java | Transactional submissions and reports |
| Database.java | JDBC connection and table initialization |
| QuizSession.java | Synchronized in-progress answers and timing state |
| Passwords.java | Salted password hashing and verification |

There are 17 application Java files. User.java contains its three small role subclasses alongside the base class. All source uses one `quiz` package, ordinary loops, explicit JDBC and small data classes. There is no Spring, ORM, web framework or generic repository framework. Swing event callbacks use short Java lambdas.

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
- All 39 included backend checks passed.
- A separate file-backed persistence check saved accounts, an approved quiz and a result, then reopened them successfully in a new JVM.
- Swing GUI checks opened Admin's Inspect view, added a question and saved a new draft through the editor, selected an answer, and verified that timer expiry saved exactly one correctly scored result and opened its report.
- Screenshots of the dashboard, inspection view, quiz window, editor and report were visually checked for layout issues.
- These checks ran on Linux with Java 17. The Windows launcher is supplied but was not executed on Windows here.
