package quiz;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static int count(String table) throws Exception {
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            r.next(); return r.getInt(1);
        }
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
        check(quizzes.findAll(creator).size() == 1, "quiz deletion works for unused quiz");
        users.update(admin, other.getId(), "Renamed", "renamed@example.com", "Participant");
        check(users.login("renamed@example.com", "testpass1").getRole().equals("Participant"), "user update changes name, email and role");
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
        System.out.println("ALL " + checks + " CHECKS PASSED");
    }
}
