package quiz;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserDAO implements Repository<User> {
    public boolean needsSetup() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM users"); ResultSet r = p.executeQuery()) {
            r.next();
            return r.getInt(1) == 0;
        }
    }
    public User setupAdmin(String name, String email, String password) throws SQLException {
        if (!needsSetup()) throw new IllegalArgumentException("The administrator has already been created.");
        return insert(name, email, password, "Admin");
    }
    public User register(String name, String email, String password, String role) throws SQLException {
        if (!role.equals("Participant") && !role.equals("Creator"))
            throw new IllegalArgumentException("Registration allows Participant or Creator only.");
        return insert(name, email, password, role);
    }
    public User create(User admin, String name, String email, String password, String role) throws SQLException {
        requireAdmin(admin);
        return insert(name, email, password, role);
    }
    private void validate(String name, String email, String role) {
        if (name.trim().isEmpty() || name.trim().length() > 100)
            throw new IllegalArgumentException("Name must contain 1 to 100 characters.");
        if (!email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.trim().length() > 200)
            throw new IllegalArgumentException("Enter a valid email address.");
        User.fromRole(0, name, email, role);
    }
    private User insert(String name, String email, String password, String role) throws SQLException {
        validate(name, email, role);
        if (password.length() < 8) throw new IllegalArgumentException("Use a password with at least 8 characters.");
        email = email.trim().toLowerCase(Locale.ROOT);
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO users(name,email,password_hash,role) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, name.trim()); p.setString(2, email);
            p.setString(3, Passwords.hash(password)); p.setString(4, role);
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                r.next();
                return User.fromRole(r.getInt(1), name.trim(), email, role);
            }
        }
    }
    public User login(String email, String password) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT * FROM users WHERE email=?")) {
            p.setString(1, email.trim().toLowerCase(Locale.ROOT));
            try (ResultSet r = p.executeQuery()) {
                if (r.next() && Passwords.matches(password, r.getString("password_hash"))) return read(r);
            }
        }
        throw new IllegalArgumentException("Email or password is incorrect.");
    }
    @Override public List<User> findAll(User viewer) throws SQLException {
        requireAdmin(viewer);
        List<User> users = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT * FROM users ORDER BY id"); ResultSet r = p.executeQuery()) {
            while (r.next()) users.add(read(r));
        }
        return users;
    }
    public void update(User admin, int id, String name, String email, String role) throws SQLException {
        requireAdmin(admin); validate(name, email, role);
        if (id == admin.getId() && !role.equals("Admin"))
            throw new IllegalArgumentException("You cannot remove your own admin role.");
        try (Connection c = DatabaseConnection.getConnection()) {
            // A creator with existing quizzes must remain a creator or admin.
            try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM quizzes WHERE creator_id=?")) {
                p.setInt(1, id);
                try (ResultSet r = p.executeQuery()) {
                    r.next();
                    if (r.getInt(1) > 0 && role.equals("Participant"))
                        throw new IllegalArgumentException("This user owns quizzes. Keep the Creator or Admin role.");
                }
            }
            try (PreparedStatement p = c.prepareStatement("UPDATE users SET name=?,email=?,role=? WHERE id=?")) {
                p.setString(1, name.trim()); p.setString(2, email.trim().toLowerCase(Locale.ROOT));
                p.setString(3, role); p.setInt(4, id); p.executeUpdate();
            }
        }
    }
    public void delete(User admin, int id) throws SQLException {
        requireAdmin(admin);
        if (id == admin.getId()) throw new IllegalArgumentException("You cannot delete the account you are using.");
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("DELETE FROM users WHERE id=?")) {
            p.setInt(1, id); p.executeUpdate();
        }
    }
    public static void requireAdmin(User user) {
        if (user == null || !user.getRole().equals("Admin")) throw new IllegalArgumentException("Administrator access required.");
    }
    private User read(ResultSet r) throws SQLException {
        return User.fromRole(r.getInt("id"), r.getString("name"), r.getString("email"), r.getString("role"));
    }
}
