package quiz;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuizDAO implements DatabaseOperations<Quiz> {
    @Override public List<Quiz> findAll(User viewer) throws SQLException {
        String sql = "SELECT * FROM quizzes";
        if (viewer.getRole().equals("Creator")) sql += " WHERE creator_id=?";
        if (viewer.getRole().equals("Participant")) sql += " WHERE status='Approved'";
        sql += " ORDER BY id DESC";
        List<Quiz> quizzes = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            if (viewer.getRole().equals("Creator")) p.setInt(1, viewer.getId());
            try (ResultSet r = p.executeQuery()) { while (r.next()) quizzes.add(read(r)); }
        }
        return quizzes;
    }
    public Quiz load(User viewer, int id) throws SQLException, QuizNotFoundException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT * FROM quizzes WHERE id=?")) {
            p.setInt(1, id);
            Quiz quiz;
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new QuizNotFoundException(id);
                quiz = read(r);
            }
            if (viewer.getRole().equals("Creator") && quiz.creatorId != viewer.getId())
                throw new IllegalArgumentException("You can only access your own quizzes.");
            if (viewer.getRole().equals("Participant") && !quiz.status.equals("Approved"))
                throw new IllegalArgumentException("This quiz is not available for participants.");
            try (PreparedStatement q = c.prepareStatement("SELECT * FROM questions WHERE quiz_id=? ORDER BY position")) {
                q.setInt(1, id);
                try (ResultSet r = q.executeQuery()) {
                    while (r.next()) quiz.questions.add(new Question(r.getString("question_text"),
                        new String[]{r.getString("option_a"), r.getString("option_b"), r.getString("option_c"), r.getString("option_d")},
                        r.getInt("correct_answer"), r.getString("explanation")));
                }
            }
            return quiz;
        }
    }
    @Override public void save(User actor, Quiz quiz) throws SQLException, QuizNotFoundException {
        if (actor.getRole().equals("Participant")) throw new IllegalArgumentException("Creator or Admin access required.");
        if (quiz.id != 0) requireOwner(actor, load(actor, quiz.id));
        if (quiz.title.trim().isEmpty() || quiz.title.trim().length() > 150
                || quiz.category.trim().isEmpty() || quiz.category.trim().length() > 80)
            throw new IllegalArgumentException("Enter a title (up to 150 characters) and category (up to 80).");
        if (quiz.durationSeconds < 10 || quiz.durationSeconds > 7200)
            throw new IllegalArgumentException("Duration must be 10 to 7200 seconds.");
        int savedId = quiz.id;
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (savedId == 0) {
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO quizzes(creator_id,title,category,duration_seconds,status) VALUES(?,?,?,?,'Draft')", Statement.RETURN_GENERATED_KEYS)) {
                        p.setInt(1, actor.getId()); p.setString(2, quiz.title.trim());
                        p.setString(3, quiz.category.trim()); p.setInt(4, quiz.durationSeconds); p.executeUpdate();
                        try (ResultSet r = p.getGeneratedKeys()) { r.next(); savedId = r.getInt(1); }
                    }
                } else {
                    try (PreparedStatement p = c.prepareStatement("UPDATE quizzes SET title=?,category=?,duration_seconds=?,status='Draft',review_note='' WHERE id=?")) {
                        p.setString(1, quiz.title.trim()); p.setString(2, quiz.category.trim());
                        p.setInt(3, quiz.durationSeconds); p.setInt(4, savedId); p.executeUpdate();
                    }
                    try (PreparedStatement p = c.prepareStatement("DELETE FROM questions WHERE quiz_id=?")) {
                        p.setInt(1, savedId); p.executeUpdate();
                    }
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO questions(quiz_id,position,question_text,option_a,option_b,option_c,option_d,correct_answer,explanation) VALUES(?,?,?,?,?,?,?,?,?)")) {
                    int position = 0;
                    for (Question question : quiz.questions) {
                        p.setInt(1, savedId); p.setInt(2, position++); p.setString(3, question.text);
                        for (int i = 0; i < 4; i++) p.setString(4 + i, question.options[i]);
                        p.setInt(8, question.correctAnswer); p.setString(9, question.explanation); p.executeUpdate();
                    }
                }
                c.commit();
                quiz.id = savedId;
                quiz.status = "Draft";
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }
    @Override public void update(User actor, Quiz quiz) throws SQLException, QuizNotFoundException {
        if (quiz.id <= 0) throw new IllegalArgumentException("Save a new quiz before updating it.");
        save(actor, quiz);
    }
    public void submit(User actor, int id) throws SQLException, QuizNotFoundException {
        Quiz quiz = load(actor, id); requireOwner(actor, quiz);
        if (quiz.questions.isEmpty()) throw new IllegalArgumentException("Add at least one question before submitting.");
        if (!quiz.status.equals("Draft") && !quiz.status.equals("Rejected"))
            throw new IllegalArgumentException("Only draft or rejected quizzes can be submitted.");
        changeStatus(id, "Pending", "");
    }
    public void review(User admin, int id, boolean approve, String note) throws SQLException, QuizNotFoundException {
        UserDAO.requireAdmin(admin);
        Quiz quiz = load(admin, id);
        if (!quiz.status.equals("Pending")) throw new IllegalArgumentException("Select a pending quiz to review.");
        if (quiz.questions.isEmpty()) throw new IllegalArgumentException("The quiz has no questions.");
        if (note.length() > 500) throw new IllegalArgumentException("Review note limit: 500 characters.");
        changeStatus(id, approve ? "Approved" : "Rejected", note);
    }
    private void changeStatus(int id, String status, String note) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE quizzes SET status=?,review_note=? WHERE id=?")) {
            p.setString(1, status); p.setString(2, note); p.setInt(3, id); p.executeUpdate();
        }
    }
    @Override public void delete(User actor, int id) throws SQLException, QuizNotFoundException {
        requireOwner(actor, load(actor, id));
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("DELETE FROM quizzes WHERE id=?")) {
            p.setInt(1, id); p.executeUpdate();
        }
    }
    private void requireOwner(User actor, Quiz quiz) {
        if (!actor.getRole().equals("Admin") && !(actor.getRole().equals("Creator") && quiz.creatorId == actor.getId()))
            throw new IllegalArgumentException("Only the owner or an admin can manage this quiz.");
    }
    private Quiz read(ResultSet r) throws SQLException {
        Quiz quiz = new Quiz(r.getInt("id"), r.getInt("creator_id"), r.getString("title"), r.getString("category"), r.getInt("duration_seconds"), r.getString("status"));
        quiz.reviewNote = r.getString("review_note");
        return quiz;
    }
}
