package quiz;

import java.util.ArrayList;
import java.util.List;

public class Quiz {
    public int id;
    public int creatorId;
    public String title;
    public String category;
    public int durationSeconds;
    public String status;
    public String reviewNote = "";
    public List<Question> questions = new ArrayList<>();

    public Quiz(int id, int creatorId, String title, String category, int durationSeconds, String status) {
        this.id = id;
        this.creatorId = creatorId;
        this.title = title;
        this.category = category;
        this.durationSeconds = durationSeconds;
        this.status = status;
    }
}
