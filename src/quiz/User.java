package quiz;

import java.util.List;

public abstract class User {
    private final int id;
    private final String name;
    private final String email;

    public User(int id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }
    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public abstract String getRole();
    public abstract List<String> getPermissions();

    public static User fromRole(int id, String name, String email, String role) {
        switch (role) {
            case "Admin": return new Admin(id, name, email);
            case "Creator": return new QuizCreator(id, name, email);
            case "Participant": return new Participant(id, name, email);
            default: throw new IllegalArgumentException("Unknown role.");
        }
    }
}

// Each kind of user supplies its own role and permissions through the same methods.
class Admin extends User {
    public Admin(int id, String name, String email) { super(id, name, email); }
    @Override public String getRole() { return "Admin"; }
    @Override public List<String> getPermissions() {
        return List.of("Manage users", "Manage all quizzes", "Approve or reject quizzes",
            "View all results");
    }
}
class QuizCreator extends User {
    public QuizCreator(int id, String name, String email) { super(id, name, email); }
    @Override public String getRole() { return "Creator"; }
    @Override public List<String> getPermissions() {
        return List.of("Manage own quizzes", "Submit quizzes for approval", "View results for own quizzes");
    }
}
class Participant extends User {
    public Participant(int id, String name, String email) { super(id, name, email); }
    @Override public String getRole() { return "Participant"; }
    @Override public List<String> getPermissions() {
        return List.of("Take approved quizzes", "View own results");
    }
}
