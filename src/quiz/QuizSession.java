package quiz;

import java.util.HashMap;
import java.util.Map;

// The Swing event thread and countdown thread share this small object.
public class QuizSession {
    private final long startedAt = System.nanoTime();
    private final int durationSeconds;
    private final Map<Integer, Integer> answers = new HashMap<>();
    private boolean finished;
    private int timeUsed;

    public QuizSession(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public synchronized int remainingSeconds() {
        long elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L;
        return Math.max(0, durationSeconds - (int) elapsed);
    }
    public synchronized boolean answer(int question, int option) {
        if (finished || remainingSeconds() == 0) return false;
        answers.put(question, option);
        return true;
    }
    public synchronized Map<Integer, Integer> getAnswers() { return new HashMap<>(answers); }
    public synchronized int getTimeUsed() { return timeUsed; }
    public synchronized boolean isFinished() { return finished; }
    public synchronized boolean finish() {
        if (finished) return false;
        finished = true;
        timeUsed = durationSeconds - remainingSeconds();
        return true;
    }
}
