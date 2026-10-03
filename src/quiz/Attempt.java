package quiz;

public class Attempt {
    public final int id;
    public final String participant;
    public final String quizTitle;
    public final int score;
    public final int total;
    public final int seconds;
    public final String finishedAt;

    public Attempt(int id, String participant, String quizTitle, int score, int total, int seconds, String finishedAt) {
        this.id = id; this.participant = participant; this.quizTitle = quizTitle;
        this.score = score; this.total = total; this.seconds = seconds; this.finishedAt = finishedAt;
    }
    public double percentage() { return 100.0 * score / total; }
}
