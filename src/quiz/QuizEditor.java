package quiz;

import java.awt.*;
import javax.swing.*;

public class QuizEditor extends JDialog {
    private final JTextField title = new JTextField();
    private final JTextField category = new JTextField();
    private final JSpinner duration = new JSpinner(new SpinnerNumberModel(120, 10, 7200, 10));
    private final DefaultListModel<Question> questions = new DefaultListModel<>();
    private final JList<Question> questionList = new JList<>(questions);
    private boolean saved;

    public QuizEditor(JFrame owner, User actor, Quiz quiz) {
        super(owner, quiz.id == 0 ? "Create quiz" : "Edit quiz", true);
        title.setText(quiz.title); category.setText(quiz.category); duration.setValue(quiz.durationSeconds);
        for (Question question : quiz.questions) questions.addElement(question);
        JPanel body = new JPanel(new BorderLayout(10, 10));
        body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel fields = UI.form();
        UI.field(fields, "Title", title); UI.field(fields, "Category", category);
        UI.field(fields, "Duration (seconds)", duration);
        body.add(fields, BorderLayout.NORTH);
        questionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        questionList.setFixedCellHeight(32);
        JScrollPane list = new JScrollPane(questionList);
        list.setBorder(BorderFactory.createTitledBorder("Questions"));
        body.add(list, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        actions.add(UI.button("Add question", () -> editQuestion(-1)));
        actions.add(UI.button("Edit question", () -> editQuestion(selected())));
        actions.add(UI.button("Remove question", () -> {
            int index = selected();
            if (UI.confirm(this, "Remove this question?")) questions.remove(index);
        }));
        actions.add(UI.button("Save draft", () -> {
            duration.commitEdit();
            quiz.title = title.getText(); quiz.category = category.getText();
            quiz.durationSeconds = (Integer) duration.getValue();
            quiz.questions.clear();
            for (int i = 0; i < questions.size(); i++) quiz.questions.add(questions.get(i));
            new QuizDAO().save(actor, quiz);
            saved = true; dispose();
        }));
        actions.add(UI.button("Cancel", () -> {
            if (UI.confirm(this, "Discard unsaved changes?")) dispose();
        }));
        body.add(actions, BorderLayout.SOUTH);
        setContentPane(body); setSize(850, 520); setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (UI.confirm(QuizEditor.this, "Discard unsaved changes?")) dispose();
            }
        });
    }
    public boolean wasSaved() { return saved; }
    private int selected() {
        int index = questionList.getSelectedIndex();
        if (index < 0) throw new IllegalArgumentException("Select a question first.");
        return index;
    }
    private void editQuestion(int index) {
        Question old = index < 0 ? null : questions.get(index);
        JTextArea text = new JTextArea(3, 35); text.setLineWrap(true); text.setWrapStyleWord(true);
        JTextField[] options = new JTextField[4];
        JComboBox<String> correct = new JComboBox<>(new String[]{"A", "B", "C", "D"});
        JTextArea explanation = new JTextArea(3, 35); explanation.setLineWrap(true); explanation.setWrapStyleWord(true);
        JPanel form = UI.form();
        UI.field(form, "Question", new JScrollPane(text));
        for (int i = 0; i < 4; i++) {
            options[i] = new JTextField(old == null ? "" : old.options[i]);
            UI.field(form, "Option " + (char) ('A' + i), options[i]);
        }
        UI.field(form, "Correct answer", correct); UI.field(form, "Explanation / feedback", new JScrollPane(explanation));
        if (old != null) { text.setText(old.text); correct.setSelectedIndex(old.correctAnswer); explanation.setText(old.explanation); }
        while (JOptionPane.showConfirmDialog(this, form, index < 0 ? "Add question" : "Edit question",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                String[] values = new String[4];
                for (int i = 0; i < 4; i++) values[i] = options[i].getText();
                Question question = new Question(text.getText(), values, correct.getSelectedIndex(), explanation.getText());
                if (index < 0) questions.addElement(question); else questions.set(index, question);
                return;
            } catch (IllegalArgumentException e) { UI.error(this, e); }
        }
    }
}
