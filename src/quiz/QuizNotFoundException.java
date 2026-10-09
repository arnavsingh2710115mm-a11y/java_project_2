package quiz;

/** Checked exception for a quiz that has been deleted or does not exist. */
public class QuizNotFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public QuizNotFoundException(int quizId) {
        super("Quiz #" + quizId + " no longer exists. Refresh the quiz list and try again.");
    }
}
