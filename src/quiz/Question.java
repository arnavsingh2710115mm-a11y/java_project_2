package quiz;

public class Question {
    public final String text;
    public final String[] options;
    public final int correctAnswer;
    public final String explanation;

    public Question(String text, String[] options, int correctAnswer, String explanation) {
        this.text = text.trim();
        this.options = options.clone();
        this.correctAnswer = correctAnswer;
        this.explanation = explanation.trim();
        if (this.text.isEmpty() || this.text.length() > 2000 || options.length != 4
                || correctAnswer < 0 || correctAnswer > 3 || this.explanation.length() > 2000) {
            throw new IllegalArgumentException("Enter a question, four options and a correct answer. Text limit: 2000 characters.");
        }
        for (int i = 0; i < 4; i++) {
            this.options[i] = this.options[i].trim();
            if (this.options[i].isEmpty() || this.options[i].length() > 500)
                throw new IllegalArgumentException("Each option needs 1 to 500 characters.");
        }
    }
    @Override public String toString() { return text; }
}
