package quiz;

import java.awt.*;
import java.sql.SQLException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class UI {
    // Lets button actions report both input errors and checked database exceptions.
    public interface Action { void run() throws Exception; }
    public static JButton button(String text, Action action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> {
            try { action.run(); } catch (Exception e) { error(button, e); }
        });
        return button;
    }
    public static void error(Component parent, Exception error) {
        error.printStackTrace();
        String message = error.getMessage();
        if (error instanceof SQLException) {
            String state = ((SQLException) error).getSQLState();
            if ("23505".equals(state)) message = "This email is already registered.";
            else if ("23503".equals(state)) message = "This item has linked quizzes or results. Keep it to preserve those records.";
            else message = "The database operation failed. Your change was not saved.\nClose any second copy of the app and try again.\nDetails are in the terminal.";
        }
        JOptionPane.showMessageDialog(parent, message, "Please check", JOptionPane.ERROR_MESSAGE);
    }
    public static JPanel form() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        return panel;
    }
    public static void field(JPanel panel, String label, Component component) {
        panel.add(new JLabel(label)); panel.add(component);
    }
    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }
    public static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }
    public static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(30); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }
    public static int selected(JTable table) {
        if (table.getSelectedRow() < 0) throw new IllegalArgumentException("Select a row first.");
        return table.convertRowIndexToModel(table.getSelectedRow());
    }
    public static void text(Component parent, String title, String content) {
        JTextArea area = new JTextArea(content, 25, 75);
        area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setCaretPosition(0); area.setMargin(new Insets(12, 12, 12, 12));
        JOptionPane.showMessageDialog(parent, new JScrollPane(area), title, JOptionPane.PLAIN_MESSAGE);
    }
}
