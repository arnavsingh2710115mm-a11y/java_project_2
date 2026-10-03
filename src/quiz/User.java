package quiz;

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

    public static User fromRole(int id, String name, String email, String role) {
        switch (role) {
            case "Admin": return new Admin(id, name, email);
            case "Creator": return new QuizCreator(id, name, email);
            case "Participant": return new Participant(id, name, email);
            default: throw new IllegalArgumentException("Unknown role.");
        }
    }
}

// Each kind of user supplies its own role through the same method.
class Admin extends User {
    public Admin(int id, String name, String email) { super(id, name, email); }
    @Override public String getRole() { return "Admin"; }
}
class QuizCreator extends User {
    public QuizCreator(int id, String name, String email) { super(id, name, email); }
    @Override public String getRole() { return "Creator"; }
}
class Participant extends User {
    public Participant(int id, String name, String email) { super(id, name, email); }
    @Override public String getRole() { return "Participant"; }
}
