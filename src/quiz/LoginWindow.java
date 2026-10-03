package quiz;

import java.awt.*;
import javax.swing.*;

public class LoginWindow extends JFrame {
    private final UserDAO users = new UserDAO();
    private final JTextField email = new JTextField(24);
    private final JPasswordField password = new JPasswordField(24);

    public LoginWindow() throws Exception {
        super("Java Quiz Platform");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
        JLabel heading = new JLabel("Java Quiz Platform");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 26f));
        body.add(heading, BorderLayout.NORTH);
        JPanel fields = UI.form();
        UI.field(fields, "Email", email); UI.field(fields, "Password", password);
        body.add(fields, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        JButton login = UI.button("Log in", () -> {
            User user = users.login(email.getText(), new String(password.getPassword()));
            new Dashboard(user).setVisible(true); dispose();
        });
        actions.add(login);
        actions.add(UI.button("Create account", () -> accountForm(false)));
        body.add(actions, BorderLayout.SOUTH);
        setContentPane(body); getRootPane().setDefaultButton(login);
        pack(); setMinimumSize(new Dimension(550, 260)); setLocationRelativeTo(null);
        if (users.needsSetup()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Welcome. Create the first administrator account to begin.");
                accountForm(true);
            });
        }
    }
    private void accountForm(boolean firstAdmin) {
        JTextField name = new JTextField(); JTextField address = new JTextField();
        JPasswordField secret = new JPasswordField();
        JComboBox<String> role = new JComboBox<>(new String[]{"Participant", "Creator"});
        JPanel form = UI.form();
        UI.field(form, "Name", name); UI.field(form, "Email", address);
        UI.field(form, "Password (8+ characters)", secret);
        if (!firstAdmin) UI.field(form, "Role", role);
        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, form, firstAdmin ? "Set up administrator" : "Create account",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                if (firstAdmin) dispose();
                return;
            }
            try {
                if (firstAdmin) users.setupAdmin(name.getText(), address.getText(), new String(secret.getPassword()));
                else users.register(name.getText(), address.getText(), new String(secret.getPassword()), (String) role.getSelectedItem());
                email.setText(address.getText()); password.setText("");
                JOptionPane.showMessageDialog(this, "Account created. You can now log in.");
                return;
            } catch (Exception e) { UI.error(this, e); }
        }
    }
}
