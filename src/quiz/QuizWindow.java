package quiz;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

public class QuizWindow extends JDialog {
    private final User participant;
    private final Quiz quiz;
    private final QuizSession session;
    private final JLabel clock = new JLabel();
    private final JLabel position = new JLabel();
    private final JTextArea question = new JTextArea(5, 55);
    private final JRadioButton[] choices = new JRadioButton[4];
    private final ButtonGroup group = new ButtonGroup();
    private final JButton previous;
    private final JButton next;
    private final JButton submit;
    private final Thread timer;
    private int index;
    private boolean saved;

    public QuizWindow(JFrame owner, User participant, Quiz quiz) {
        super(owner, "Take quiz - " + quiz.title, true);
        if (quiz.questions.isEmpty()) throw new IllegalArgumentException("This quiz has no questions.");
        this.participant = participant; this.quiz = quiz;
        session = new QuizSession(quiz.durationSeconds);
        JPanel body = new JPanel(new BorderLayout(16, 16));
        body.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JPanel header = new JPanel(new BorderLayout());
        header.add(position, BorderLayout.WEST); header.add(clock, BorderLayout.EAST);
        clock.setFont(clock.getFont().deriveFont(Font.BOLD, 18f)); body.add(header, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(12, 12));
        question.setEditable(false); question.setLineWrap(true); question.setWrapStyleWord(true);
        question.setFont(question.getFont().deriveFont(18f)); question.setBackground(body.getBackground());
        center.add(new JScrollPane(question), BorderLayout.NORTH);
        JPanel options = new JPanel(new GridLayout(4, 1, 8, 8));
        for (int i = 0; i < 4; i++) {
            final int option = i;
            choices[i] = new JRadioButton(); group.add(choices[i]); options.add(choices[i]);
            choices[i].addActionListener(e -> {
                if (!session.answer(index, option)) finish();
                else updatePosition();
            });
        }
        center.add(new JScrollPane(options), BorderLayout.CENTER); body.add(center, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        previous = UI.button("Previous", () -> { if (index > 0) { index--; showQuestion(); } });
        next = UI.button("Next", () -> { if (index < quiz.questions.size() - 1) { index++; showQuestion(); } });
        submit = UI.button("Submit quiz", () -> {
            if (session.isFinished() || UI.confirm(this, "Submit now? Unanswered questions score zero.")) finish();
        });
        actions.add(previous); actions.add(next); actions.add(submit); body.add(actions, BorderLayout.SOUTH);
        setContentPane(body); setSize(800, 540); setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (session.isFinished() || UI.confirm(QuizWindow.this, "Leaving submits your current answers. Continue?")) finish();
            }
        });
        showQuestion(); updateClock();
        // This background thread only changes shared session state. Swing updates run on its event thread.
        timer = new Thread(() -> {
            try {
                while (!session.isFinished()) {
                    Thread.sleep(200);
                    if (session.remainingSeconds() == 0) {
                        if (session.finish()) SwingUtilities.invokeLater(() -> finish());
                        return;
                    }
                    SwingUtilities.invokeLater(() -> updateClock());
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "quiz-countdown");
        timer.setDaemon(true); timer.start();
    }
    private void updateClock() {
        int left = session.remainingSeconds(); clock.setText(String.format("Time left: %02d:%02d", left / 60, left % 60));
    }
    private void updatePosition() {
        position.setText("Question " + (index + 1) + " / " + quiz.questions.size() + "    Answered: " + session.getAnswers().size());
    }
    private void showQuestion() {
        Question current = quiz.questions.get(index); question.setText(current.text); question.setCaretPosition(0);
        group.clearSelection();
        int selected = session.getAnswers().getOrDefault(index, -1);
        for (int i = 0; i < 4; i++) {
            choices[i].setText((char) ('A' + i) + ". " + current.options[i]);
            choices[i].setSelected(selected == i);
        }
        previous.setEnabled(index > 0); next.setEnabled(index < quiz.questions.size() - 1); updatePosition();
    }
    private void finish() {
        if (saved) return;
        session.finish(); timer.interrupt();
        previous.setEnabled(false); next.setEnabled(false);
        for (JRadioButton choice : choices) choice.setEnabled(false);
        submit.setText("Retry saving result");
        clock.setText("Quiz finished");
        try {
            ResultDAO results = new ResultDAO();
            int attemptId = results.save(participant, quiz, session.getAnswers(), session.getTimeUsed());
            saved = true;
            dispose();
            UI.text(getOwner(), "Your result", results.report(participant, attemptId));
        } catch (Exception e) { UI.error(this, e); }
    }
}
