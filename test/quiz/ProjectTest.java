package quiz;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ProjectTest {
    private static int checks;
    private interface Checked { void run() throws Exception; }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++; System.out.println("PASS " + message);
    }
    private static void fails(Checked action, String message) throws Exception {
        try { action.run(); } catch (IllegalArgumentException | SQLException e) { check(true, message); return; }
        throw new AssertionError("Expected failure: " + message);
    }
    private static void missingQuiz(Checked action, int quizId, String message) throws Exception {
        try { action.run(); }
        catch (QuizNotFoundException e) {
            check(e.getMessage().contains("Quiz #" + quizId), message);
            return;
        }
        throw new AssertionError("Expected QuizNotFoundException: " + message);
    }
    private static int count(String table) throws Exception {
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            r.next(); return r.getInt(1);
        }
    }
    private static void expireAttempt(String id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE web_attempts SET deadline=? WHERE id=?")) {
            p.setLong(1, System.currentTimeMillis() - 1); p.setString(2, id); p.executeUpdate();
        }
    }
    private static Integer savedWebResult(String id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT result_id FROM web_attempts WHERE id=?")) {
            p.setString(1, id);
            try (ResultSet r = p.executeQuery()) { r.next(); return (Integer) r.getObject(1); }
        }
    }
    private static void rubricAdditions(User admin, User creator, User participant, User secondStudent) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT 1"); ResultSet r = p.executeQuery()) {
            r.next(); check(r.getInt(1) == 1, "named connection class opens a working JDBC connection");
        }
        DataRepository<User> userIndex = new DataRepository<>();
        for (User user : new UserDAO().findAll(admin)) userIndex.put(user.getId(), user);
        check(userIndex.findById(creator.getId()).getPermissions().equals(creator.getPermissions()),
            "generic HashMap index retrieves a role-polymorphic User by ID");
        userIndex.clear();
        check(userIndex.size() == 0 && userIndex.findById(creator.getId()) == null, "refresh clears stale indexed users");
        fails(() -> new Question("Duplicate options?", new String[]{"new", " NEW ", "class", "void"}, 0, ""),
            "HashSet rejects duplicate options after trimming and case normalization");

        DatabaseOperations<Quiz> operations = new QuizDAO();
        QuizDAO quizzes = new QuizDAO();
        Question question = new Question("Which keyword creates an object?", new String[]{"new", "class", "void", "return"}, 0, "new creates an object.");
        Quiz crud = new Quiz(0, creator.getId(), "Interface CRUD", "Practice", 30, "Draft");
        crud.questions.add(question); operations.save(creator, crud);
        check(crud.id > 0 && operations.findAll(creator).stream().anyMatch(q -> q.id == crud.id), "CRUD interface creates and reads a persisted quiz");
        crud.title = "Updated through interface"; operations.update(creator, crud);
        check(quizzes.load(creator, crud.id).title.equals(crud.title), "CRUD interface updates a persisted quiz");
        fails(() -> operations.update(participant, crud), "CRUD interface preserves participant restrictions");
        fails(() -> operations.update(creator, new Quiz(0, creator.getId(), "Unsaved", "Practice", 30, "Draft")),
            "interface update cannot silently create an unsaved quiz");
        operations.delete(creator, crud.id);
        missingQuiz(() -> quizzes.load(creator, crud.id), crud.id, "CRUD interface deletes an unused quiz");

        Quiz atomic = new Quiz(0, creator.getId(), "Original atomic quiz", "Practice", 30, "Draft");
        atomic.questions.add(question); operations.save(creator, atomic);
        atomic.title = "Title that must roll back";
        atomic.questions.add(new Question("Which type stores true or false?", new String[]{"boolean", "int", "char", "double"}, 0, ""));
        atomic.questions.get(1).options[2] = null; // Force a real SQL constraint failure after the first insert.
        fails(() -> operations.update(creator, atomic), "invalid second question forces JDBC transaction rollback");
        Quiz restored = quizzes.load(creator, atomic.id);
        check(restored.title.equals("Original atomic quiz") && restored.questions.size() == 1 && restored.questions.get(0).text.equals(question.text),
            "quiz rollback restores original title and question rows");
        operations.delete(creator, atomic.id);

        WebStore.initialize();
        Quiz timed = new Quiz(0, creator.getId(), "Background maintenance", "Practice", 30, "Draft");
        timed.questions.add(question); operations.save(creator, timed);
        quizzes.submit(creator, timed.id); quizzes.review(admin, timed.id, true, "");
        String id = (String) WebStore.start(participant, timed.id).get("id");
        WebStore.access(participant, id, Map.of("question", 0, "option", 0), false);
        expireAttempt(id);
        int before = count("attempts");
        Runnable service = new QuizMaintenanceService();
        Thread background = new Thread(service, "test-quiz-maintenance");
        background.start(); background.join(5000);
        check(!background.isAlive() && savedWebResult(id) != null && count("attempts") == before + 1,
            "Runnable background thread submits an expired attempt without a browser");
        check(((Number) WebStore.report(participant, savedWebResult(id)).get("score")).intValue() == 1,
            "generic result index preserves the correctly scored report");
        fails(() -> WebStore.report(secondStudent, savedWebResult(id)), "generic result index does not expose another participant's report");
        service.run();
        check(count("attempts") == before + 1, "repeated background maintenance does not duplicate a result");

        String racingId = (String) WebStore.start(participant, timed.id).get("id");
        WebStore.access(participant, racingId, Map.of("question", 0, "option", 0), false);
        expireAttempt(racingId);
        int raceBefore = count("attempts");
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread maintenance = new Thread(() -> {
            try { start.await(); service.run(); } catch (Exception e) { failure.set(e); }
        }, "test-expiry-race");
        Thread submit = new Thread(() -> {
            try { start.await(); WebStore.access(participant, racingId, null, true); } catch (Exception e) { failure.set(e); }
        }, "test-submit-race");
        maintenance.start(); submit.start(); start.countDown(); maintenance.join(5000); submit.join(5000);
        check(!maintenance.isAlive() && !submit.isAlive() && failure.get() == null && savedWebResult(racingId) != null && count("attempts") == raceBefore + 1,
            "synchronized expiry and manual submission save exactly one result");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("quiz.db", "jdbc:h2:mem:checks;DB_CLOSE_DELAY=-1");
        Database.initialize();
        UserDAO users = new UserDAO(); QuizDAO quizzes = new QuizDAO(); ResultDAO results = new ResultDAO();
        check(users.needsSetup(), "fresh database needs setup");
        User admin = users.setupAdmin("Admin", "admin@example.com", "testpass1");
        fails(() -> users.setupAdmin("Other", "other@example.com", "testpass1"), "second bootstrap blocked");
        User creator = users.create(admin, "Creator", "creator@example.com", "testpass1", "Creator");
        User participant = users.register("Student", "student@example.com", "testpass1", "Participant");
        User other = users.register("Other Creator", "other@example.com", "testpass1", "Creator");
        User secondStudent = users.register("Student Two", "two@example.com", "testpass1", "Participant");
        check(admin.getPermissions().equals(List.of("Manage users", "Manage all quizzes", "Approve or reject quizzes", "View all results")),
            "admin permissions dispatch through a User reference");
        check(creator.getPermissions().equals(List.of("Manage own quizzes", "Submit quizzes for approval", "View results for own quizzes")),
            "creator permissions dispatch through a User reference");
        check(participant.getPermissions().equals(List.of("Take approved quizzes", "View own results")),
            "participant permissions dispatch through a User reference");
        for (User roleUser : List.of(admin, creator, participant)) {
            Map<String,Object> payload = Json.object(Json.parse(Json.write(WebStore.user(roleUser))));
            check(payload.get("permissions").equals(roleUser.getPermissions()), roleUser.getRole() + " permissions reach the profile JSON");
        }
        try {
            participant.getPermissions().add("Manage users");
            throw new AssertionError("Permissions must be immutable.");
        } catch (UnsupportedOperationException e) { check(true, "returned permissions cannot be modified"); }
        check(users.login("STUDENT@example.com", "testpass1").getId() == participant.getId(), "login and email normalization");
        fails(() -> users.login("student@example.com", "incorrect"), "wrong password rejected");
        fails(() -> users.register("Student", "student@example.com", "testpass1", "Participant"), "duplicate email rejected");
        fails(() -> users.register("Admin", "bad@example.com", "testpass1", "Admin"), "public admin registration blocked");
        check(!Passwords.hash("testpass1").equals(Passwords.hash("testpass1")), "passwords use unique salts");
        fails(() -> users.findAll(participant), "participant cannot list users");
        fails(() -> users.delete(admin, admin.getId()), "active admin cannot delete own account");
        Quiz quiz = new Quiz(0, creator.getId(), "Java fundamentals", "Java basics", 10, "Draft");
        quizzes.save(creator, quiz);
        fails(() -> quizzes.submit(creator, quiz.id), "empty quiz cannot be submitted");
        quiz.questions.add(new Question("Which type stores true or false?", new String[]{"int", "boolean", "char", "double"}, 1, "boolean stores true or false."));
        quiz.questions.add(new Question("Which keyword creates an object?", new String[]{"new", "class", "void", "return"}, 0, "new creates an object."));
        quizzes.save(creator, quiz);
        check(quizzes.load(admin, quiz.id).questions.size() == 2, "admin can inspect saved questions");
        check(quizzes.findAll(participant).isEmpty(), "draft hidden from participant");
        fails(() -> quizzes.load(other, quiz.id), "creator cannot inspect another creator's quiz");
        fails(() -> quizzes.load(participant, quiz.id), "participant cannot load a draft");
        quizzes.submit(creator, quiz.id);
        fails(() -> quizzes.review(creator, quiz.id, true, ""), "creator cannot approve quiz");
        quizzes.review(admin, quiz.id, false, "Please review wording");
        check(quizzes.load(creator, quiz.id).reviewNote.equals("Please review wording"), "rejection reason saved");
        quizzes.submit(creator, quiz.id); quizzes.review(admin, quiz.id, true, "Ready");
        Quiz approved = quizzes.load(participant, quiz.id);
        check(quizzes.findAll(participant).size() == 1, "approved quiz appears for participant");
        Map<Integer, Integer> answers = new HashMap<>(); answers.put(0, 1);
        int attempt = results.save(participant, approved, answers, 5);
        check(results.findAll(participant).get(0).score == 1, "scoring handles an unanswered question");
        check(count("answers") == 2, "submission stores every answer");
        check(results.report(participant, attempt).contains("Unanswered"), "detailed report includes skipped questions");
        check(results.findAll(creator).size() == 1 && results.findAll(admin).size() == 1, "creator and admin can view results");
        check(results.findAll(other).isEmpty(), "other creator cannot list results");
        fails(() -> results.report(secondStudent, attempt), "other participant cannot read report");
        int before = count("attempts");
        Map<Integer, Integer> invalid = new HashMap<>(); invalid.put(1, 99);
        fails(() -> results.save(participant, approved, invalid, 5), "invalid second answer forces transaction rollback");
        check(count("attempts") == before && count("answers") == 2, "rollback leaves no partial result or answer rows");
        quiz.title = "Updated title"; quiz.questions.remove(1); quizzes.save(creator, quiz);
        check(quizzes.findAll(participant).isEmpty(), "editing unpublishes an approved quiz");
        check(results.report(participant, attempt).contains("Which keyword creates an object?"), "old report preserves original questions");
        check(results.report(participant, attempt).startsWith("Java fundamentals"), "old report preserves original title");
        fails(() -> quizzes.delete(admin, quiz.id), "quiz with results protected from deletion");
        fails(() -> users.delete(admin, creator.getId()), "creator with quizzes protected from deletion");
        Quiz disposable = new Quiz(0, creator.getId(), "Delete me", "Practice", 30, "Draft");
        disposable.questions.add(approved.questions.get(0)); quizzes.save(creator, disposable);
        quizzes.delete(creator, disposable.id);
        missingQuiz(() -> quizzes.load(creator, disposable.id), disposable.id, "loading a deleted quiz throws the custom checked exception");
        missingQuiz(() -> quizzes.save(creator, disposable), disposable.id, "saving a deleted quiz propagates the custom exception");
        missingQuiz(() -> quizzes.submit(creator, disposable.id), disposable.id, "submitting a deleted quiz propagates the custom exception");
        missingQuiz(() -> quizzes.review(admin, disposable.id, true, ""), disposable.id, "reviewing a deleted quiz propagates the custom exception");
        missingQuiz(() -> quizzes.delete(creator, disposable.id), disposable.id, "deleting an already deleted quiz propagates the custom exception");
        missingQuiz(() -> WebStore.start(participant, disposable.id), disposable.id, "starting a deleted quiz propagates the custom exception");
        check(quizzes.findAll(creator).size() == 1, "quiz deletion works for unused quiz");
        users.update(admin, other.getId(), "Renamed", "renamed@example.com", "Participant");
        check(users.login("renamed@example.com", "testpass1").getRole().equals("Participant"), "user update changes name, email and role");
        check(users.login("renamed@example.com", "testpass1").getPermissions().equals(participant.getPermissions()),
            "updated user role supplies its new permissions");
        users.delete(admin, other.getId());
        check(users.findAll(admin).size() == 4, "unused user can be deleted");
        QuizSession session = new QuizSession(10);
        check(session.answer(0, 1), "active session accepts answer");
        Map<Integer, Integer> copy = session.getAnswers(); copy.put(0, 3);
        check(session.getAnswers().get(0) == 1, "answer snapshot cannot mutate shared state");
        CountDownLatch start = new CountDownLatch(1); AtomicInteger winners = new AtomicInteger();
        Runnable finish = () -> { try { start.await(); if (session.finish()) winners.incrementAndGet(); } catch (InterruptedException e) { throw new RuntimeException(e); } };
        Thread a = new Thread(finish); Thread b = new Thread(finish); a.start(); b.start(); start.countDown(); a.join(); b.join();
        check(winners.get() == 1, "concurrent finish calls have exactly one winner");
        check(!session.answer(1, 0), "finished session rejects answer changes");
        QuizSession expired = new QuizSession(1); Thread.sleep(1100);
        check(expired.remainingSeconds() == 0 && !expired.answer(0, 0), "deadline rejects late answers");
        expired.finish(); check(expired.getTimeUsed() == 1, "expiry records full duration");
        rubricAdditions(admin, creator, participant, secondStudent);
        System.out.println("ALL " + checks + " CHECKS PASSED");
    }
}
