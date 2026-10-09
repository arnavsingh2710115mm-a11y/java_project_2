package quiz;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResultDAO implements Repository<Attempt> {
    public int save(User participant, Quiz quiz, Map<Integer, Integer> answers, int timeUsed) throws SQLException {
        if (!participant.getRole().equals("Participant")) throw new IllegalArgumentException("Participant access required.");
        if (quiz.questions.isEmpty()) throw new IllegalArgumentException("An empty quiz cannot be submitted.");
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int id = saveInTransaction(c, participant, quiz, answers, timeUsed);
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
    // The web attempt and result are committed together, preventing duplicate results.
    public int saveInTransaction(Connection c, User participant, Quiz quiz,
            Map<Integer,Integer> answers, int timeUsed) throws SQLException {
        if (!participant.getRole().equals("Participant")) throw new IllegalArgumentException("Participant access required.");
        if (quiz.questions.isEmpty()) throw new IllegalArgumentException("An empty quiz cannot be submitted.");
        int score = 0;
        for (int i=0;i<quiz.questions.size();i++) if(answers.getOrDefault(i,-1)==quiz.questions.get(i).correctAnswer) score++;
        int attemptId;
        try (PreparedStatement p=c.prepareStatement("INSERT INTO attempts(participant_id,quiz_id,quiz_title,score,total,time_used) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
            p.setInt(1,participant.getId());p.setInt(2,quiz.id);p.setString(3,quiz.title);p.setInt(4,score);p.setInt(5,quiz.questions.size());p.setInt(6,timeUsed);p.executeUpdate();
            try(ResultSet r=p.getGeneratedKeys()){r.next();attemptId=r.getInt(1);}
        }
        try(PreparedStatement p=c.prepareStatement("INSERT INTO answers(attempt_id,position,question_text,options_text,selected_answer,correct_answer,explanation) VALUES(?,?,?,?,?,?,?)")) {
            for(int i=0;i<quiz.questions.size();i++){
                Question q=quiz.questions.get(i);StringBuilder options=new StringBuilder();
                for(int j=0;j<4;j++)options.append((char)('A'+j)).append(". ").append(q.options[j]).append("\n");
                p.setInt(1,attemptId);p.setInt(2,i);p.setString(3,q.text);p.setString(4,options.toString());p.setInt(5,answers.getOrDefault(i,-1));p.setInt(6,q.correctAnswer);p.setString(7,q.explanation);p.executeUpdate();
            }
        }
        return attemptId;
    }
    @Override public List<Attempt> findAll(User viewer) throws SQLException {
        String sql = "SELECT a.*,u.name FROM attempts a JOIN users u ON a.participant_id=u.id JOIN quizzes q ON a.quiz_id=q.id";
        if (viewer.getRole().equals("Participant")) sql += " WHERE a.participant_id=?";
        if (viewer.getRole().equals("Creator")) sql += " WHERE q.creator_id=?";
        sql += " ORDER BY a.id DESC";
        List<Attempt> attempts = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            if (!viewer.getRole().equals("Admin")) p.setInt(1, viewer.getId());
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) attempts.add(new Attempt(r.getInt("id"), r.getString("name"), r.getString("quiz_title"),
                    r.getInt("score"), r.getInt("total"), r.getInt("time_used"), r.getString("finished_at")));
            }
        }
        return attempts;
    }
    public String report(User viewer, int id) throws SQLException {
        Attempt attempt = null;
        for (Attempt item : findAll(viewer)) if (item.id == id) attempt = item;
        if (attempt == null) throw new IllegalArgumentException("You cannot view this result.");
        StringBuilder report = new StringBuilder();
        report.append(attempt.quizTitle).append("\nParticipant: ").append(attempt.participant)
            .append("\nScore: ").append(attempt.score).append(" / ").append(attempt.total)
            .append(String.format(" (%.1f%%)", attempt.percentage())).append("\nTime used: ")
            .append(attempt.seconds).append(" seconds\nSubmitted: ").append(attempt.finishedAt).append("\n\n");
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT * FROM answers WHERE attempt_id=? ORDER BY position")) {
            p.setInt(1, id);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    int selected = r.getInt("selected_answer");
                    int correct = r.getInt("correct_answer");
                    report.append(r.getInt("position") + 1).append(". ").append(r.getString("question_text"))
                        .append("\n").append(r.getString("options_text"))
                        .append("Your answer: ").append(selected == -1 ? "Unanswered" : String.valueOf((char) ('A' + selected)))
                        .append(" | Correct: ").append((char) ('A' + correct))
                        .append(" | ").append(selected == correct ? "Correct" : "Incorrect / unanswered")
                        .append("\nExplanation: ").append(r.getString("explanation")).append("\n\n");
                }
            }
        }
        return report.toString();
    }
}
