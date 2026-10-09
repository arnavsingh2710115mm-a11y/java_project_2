package quiz;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class Dashboard extends JFrame {
    private final User user;
    private final QuizDAO quizzes = new QuizDAO();
    private final DatabaseOperations<Quiz> quizOperations = quizzes;
    private final UserDAO users = new UserDAO();
    private final ResultDAO results = new ResultDAO();
    private final DefaultTableModel quizModel = UI.model("ID", "Title", "Category", "Seconds", "Status", "Owner ID", "Review note");
    private final DefaultTableModel userModel = UI.model("ID", "Name", "Email", "Role");
    private final DefaultTableModel resultModel = UI.model("ID", "Participant", "Quiz", "Score", "Percent", "Seconds", "Submitted");
    private final JTable quizTable = UI.table(quizModel);
    private final JTable userTable = UI.table(userModel);
    private final JTable resultTable = UI.table(resultModel);
    private final JComboBox<String> category = new JComboBox<>();
    private final JLabel quizHint = new JLabel();
    private final JLabel resultSummary = new JLabel();
    private List<Quiz> visibleQuizzes = new ArrayList<>();
    private final DataRepository<User> visibleUsers = new DataRepository<>();
    private boolean refreshing;

    public Dashboard(User user) throws Exception {
        super("Java Quiz Platform - " + user.getRole());
        this.user = user;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JPanel body = new JPanel(new BorderLayout(16, 16));
        body.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JPanel header = new JPanel(new BorderLayout());
        JLabel heading = new JLabel(user.getName() + "  |  " + user.getRole());
        heading.setToolTipText(String.join("; ", user.getPermissions()));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 23f)); header.add(heading, BorderLayout.WEST);
        header.add(UI.button("Log out", () -> { new LoginWindow().setVisible(true); dispose(); }), BorderLayout.EAST);
        body.add(header, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(user.getRole().equals("Participant") ? "Available quizzes" : "Manage quizzes", quizPanel());
        if (user.getRole().equals("Admin")) tabs.addTab("Manage users", userPanel());
        tabs.addTab(user.getRole().equals("Participant") ? "My results" : "Quiz results", resultPanel());
        body.add(tabs, BorderLayout.CENTER);
        JLabel footer = new JLabel("Java Quiz Platform  |  Changes are saved to the local database");
        footer.setForeground(Color.DARK_GRAY); body.add(footer, BorderLayout.SOUTH);
        setContentPane(body); setSize(1100, 680); setMinimumSize(new Dimension(950, 560)); setLocationRelativeTo(null);
        refresh();
    }
    private JPanel quizPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.add(new JLabel("Category:")); filters.add(category); filters.add(UI.button("Refresh", () -> refresh()));
        category.addActionListener(e -> { if (!refreshing) fillQuizzes(); });
        panel.add(filters, BorderLayout.NORTH); panel.add(new JScrollPane(quizTable), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout()); JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        if (user.getRole().equals("Participant")) {
            actions.add(UI.button("Take selected quiz", () -> {
                Quiz quiz = quizzes.load(user, selectedQuizId());
                if (UI.confirm(this, "Start " + quiz.title + "?\nTime: " + quiz.durationSeconds + " seconds. The timer starts immediately.")) {
                    new QuizWindow(this, user, quiz).setVisible(true); refresh();
                }
            }));
            quizHint.setText("Select an approved quiz, then click Take selected quiz.");
        } else {
            actions.add(UI.button("Create quiz", () -> edit(new Quiz(0, user.getId(), "", "Java basics", 120, "Draft"))));
            actions.add(UI.button("Inspect", () -> inspect()));
            actions.add(UI.button("Edit", () -> {
                Quiz quiz = quizzes.load(user, selectedQuizId());
                if (quiz.status.equals("Approved") && !UI.confirm(this, "Saving changes will unpublish this quiz until it is approved again. Continue?")) return;
                edit(quiz);
            }));
            actions.add(UI.button("Submit for approval", () -> { quizzes.submit(user, selectedQuizId()); refresh(); }));
            if (user.getRole().equals("Admin")) {
                actions.add(UI.button("Approve", () -> review(true)));
                actions.add(UI.button("Reject", () -> review(false)));
            }
            actions.add(UI.button("Delete", () -> {
                int id = selectedQuizId();
                if (UI.confirm(this, "Delete this quiz and its questions? Quizzes with results are protected.")) {
                    quizOperations.delete(user, id); refresh();
                }
            }));
            quizHint.setText("Create > add questions > save draft > submit for approval > admin approves.");
        }
        bottom.add(actions, BorderLayout.CENTER); bottom.add(quizHint, BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH); return panel;
    }
    private JPanel userPanel() {
        JPanel panel = new JPanel(new BorderLayout()); panel.add(new JScrollPane(userTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(UI.button("Add user", () -> userForm(null)));
        actions.add(UI.button("Edit user", () -> userForm(selectedUser())));
        actions.add(UI.button("Delete user", () -> {
            User selected = selectedUser();
            if (UI.confirm(this, "Delete " + selected.getName() + "? Accounts with quizzes or results are protected.")) {
                users.delete(user, selected.getId()); refresh();
            }
        }));
        actions.add(UI.button("Refresh", () -> refresh()));
        panel.add(actions, BorderLayout.SOUTH); return panel;
    }
    private JPanel resultPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(resultSummary, BorderLayout.NORTH); panel.add(new JScrollPane(resultTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(UI.button("View detailed report", () -> {
            int row = UI.selected(resultTable); int id = (Integer) resultModel.getValueAt(row, 0);
            UI.text(this, "Performance report", results.report(user, id));
        }));
        actions.add(UI.button("Refresh", () -> refresh())); panel.add(actions, BorderLayout.SOUTH); return panel;
    }
    private void refresh() throws Exception {
        visibleQuizzes = quizOperations.findAll(user);
        String selected = (String) category.getSelectedItem();
        // A Set collects categories without duplicates and keeps the filter alphabetic.
        Set<String> categories = new TreeSet<>();
        for (Quiz quiz : visibleQuizzes) categories.add(quiz.category);
        refreshing = true;
        category.removeAllItems(); category.addItem("All categories");
        for (String item : categories) category.addItem(item);
        if (selected != null && categories.contains(selected)) category.setSelectedItem(selected);
        refreshing = false; fillQuizzes();
        if (user.getRole().equals("Admin")) {
            visibleUsers.clear(); userModel.setRowCount(0);
            for (User item : users.findAll(user)) {
                visibleUsers.put(item.getId(), item);
                userModel.addRow(new Object[]{item.getId(), item.getName(), item.getEmail(), item.getRole()});
            }
        }
        List<Attempt> attempts = results.findAll(user); resultModel.setRowCount(0);
        int earned = 0; int possible = 0;
        for (Attempt attempt : attempts) {
            resultModel.addRow(new Object[]{attempt.id, attempt.participant, attempt.quizTitle, attempt.score + " / " + attempt.total,
                String.format("%.1f%%", attempt.percentage()), attempt.seconds, attempt.finishedAt});
            earned += attempt.score; possible += attempt.total;
        }
        resultSummary.setText(attempts.size() + " attempts   |   Overall accuracy: " + (possible == 0 ? "No results yet" : String.format("%.1f%%", 100.0 * earned / possible)));
    }
    private void fillQuizzes() {
        quizModel.setRowCount(0);
        String filter = (String) category.getSelectedItem();
        for (Quiz quiz : visibleQuizzes) {
            if (filter == null || filter.equals("All categories") || filter.equals(quiz.category))
                quizModel.addRow(new Object[]{quiz.id, quiz.title, quiz.category, quiz.durationSeconds, quiz.status, quiz.creatorId, quiz.reviewNote});
        }
    }
    private int selectedQuizId() { return (Integer) quizModel.getValueAt(UI.selected(quizTable), 0); }
    private User selectedUser() {
        int id = (Integer) userModel.getValueAt(UI.selected(userTable), 0);
        User selected = visibleUsers.findById(id);
        if (selected == null) throw new IllegalArgumentException("Refresh the user list and select an account.");
        return selected;
    }
    private void edit(Quiz quiz) throws Exception {
        QuizEditor editor = new QuizEditor(this, user, quiz); editor.setVisible(true);
        if (editor.wasSaved()) refresh();
    }
    private void inspect() throws Exception {
        Quiz quiz = quizzes.load(user, selectedQuizId());
        StringBuilder text = new StringBuilder(quiz.title + "\nCategory: " + quiz.category + "\nDuration: " + quiz.durationSeconds
            + " seconds\nStatus: " + quiz.status + "\nReview note: " + quiz.reviewNote + "\n\n");
        for (int i = 0; i < quiz.questions.size(); i++) {
            Question question = quiz.questions.get(i);
            text.append(i + 1).append(". ").append(question.text).append("\n");
            for (int j = 0; j < 4; j++) text.append((char) ('A' + j)).append(". ").append(question.options[j]).append("\n");
            text.append("Correct: ").append((char) ('A' + question.correctAnswer)).append("\nExplanation: ").append(question.explanation).append("\n\n");
        }
        if (quiz.questions.isEmpty()) text.append("No questions added yet.");
        UI.text(this, "Inspect quiz", text.toString());
    }
    private void review(boolean approve) throws Exception {
        int id = selectedQuizId();
        String note = JOptionPane.showInputDialog(this, approve ? "Approval note (optional):" : "Reason for rejection:");
        if (note == null) return;
        if (!approve && note.trim().isEmpty()) throw new IllegalArgumentException("Enter a reason so the creator knows what to change.");
        quizzes.review(user, id, approve, note.trim()); refresh();
    }
    private void userForm(User existing) throws Exception {
        JTextField name = new JTextField(existing == null ? "" : existing.getName());
        JTextField email = new JTextField(existing == null ? "" : existing.getEmail());
        JPasswordField password = new JPasswordField();
        JComboBox<String> role = new JComboBox<>(new String[]{"Participant", "Creator", "Admin"});
        if (existing != null) role.setSelectedItem(existing.getRole());
        JPanel form = UI.form(); UI.field(form, "Name", name); UI.field(form, "Email", email); UI.field(form, "Role", role);
        if (existing == null) UI.field(form, "Password (8+ characters)", password);
        while (JOptionPane.showConfirmDialog(this, form, existing == null ? "Add user" : "Edit user",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                if (existing == null) users.create(user, name.getText(), email.getText(), new String(password.getPassword()), (String) role.getSelectedItem());
                else users.update(user, existing.getId(), name.getText(), email.getText(), (String) role.getSelectedItem());
                refresh(); return;
            } catch (Exception e) { UI.error(this, e); }
        }
    }
}
