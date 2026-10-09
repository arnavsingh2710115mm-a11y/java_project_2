package quiz;

import java.sql.*;
import java.util.*;
import java.nio.file.*;

/** Server-side, durable quiz attempts. The browser never receives the answer key. */
public final class WebStore {
    public static void initialize() throws Exception {
        try(Connection c=Database.connect();Statement s=c.createStatement()){
            for(String sql:Files.readString(Path.of("database/web-schema.sql")).split(";"))if(!sql.isBlank())s.execute(sql);
        }
    }
    public static Map<String,Object> user(User u){
        // u is a User reference; Java calls the actual role's overridden method.
        return Map.of("id",u.getId(),"name",u.getName(),"email",u.getEmail(),"role",u.getRole(),"permissions",u.getPermissions());
    }
    public static User findUser(Connection c,int id) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT * FROM users WHERE id=?")){
            p.setInt(1,id);try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new IllegalArgumentException("Account no longer exists.");
                return User.fromRole(id,r.getString("name"),r.getString("email"),r.getString("role"));
            }
        }
    }
    public static Map<String,Object> quiz(Quiz q,boolean answers){
        Map<String,Object> data=new LinkedHashMap<>();data.put("id",q.id);data.put("creatorId",q.creatorId);data.put("title",q.title);data.put("category",q.category);
        data.put("durationSeconds",q.durationSeconds);data.put("status",q.status);data.put("reviewNote",q.reviewNote);
        List<Object> questions=new ArrayList<>();for(Question question:q.questions){
            Map<String,Object> item=new LinkedHashMap<>();item.put("text",question.text);item.put("options",question.options);
            if(answers){item.put("correctAnswer",question.correctAnswer);item.put("explanation",question.explanation);}questions.add(item);
        }data.put("questions",questions);return data;
    }
    public static Quiz decodeQuiz(Map<String,Object> data){
        Quiz q=new Quiz(Json.number(data,"id"),Json.number(data,"creatorId"),Json.string(data,"title"),Json.string(data,"category"),Json.number(data,"durationSeconds"),Json.string(data,"status"));
        if(!(data.get("questions") instanceof List<?> questions)||questions.size()>100)throw new IllegalArgumentException("Use at most 100 questions.");
        for(Object value:questions){Map<String,Object> item=Json.object(value);
            if(!(item.get("options") instanceof List<?> options)||options.size()!=4)throw new IllegalArgumentException("Each question needs four options.");
            String[] text=new String[4];for(int i=0;i<4;i++){if(!(options.get(i) instanceof String s))throw new IllegalArgumentException("Invalid option.");text[i]=s;}
            Set<String> unique=new HashSet<>();for(String s:text)unique.add(s.trim().toLowerCase(Locale.ROOT));
            if(unique.size()!=4)throw new IllegalArgumentException("The four options must be different.");
            q.questions.add(new Question(Json.string(item,"text"),text,Json.number(item,"correctAnswer"),Json.string(item,"explanation")));
        }return q;
    }
    public static synchronized Map<String,Object> start(User user,int quizId) throws SQLException, QuizNotFoundException {
        if(!user.getRole().equals("Participant"))throw new IllegalArgumentException("Log in as a participant to attempt a quiz.");
        Quiz q=new QuizDAO().load(user,quizId);if(q.questions.isEmpty())throw new IllegalArgumentException("This quiz has no questions.");
        String id=null;
        try(Connection c=Database.connect()){
            // Lock the user row so double clicks or two tabs resume one active attempt.
            c.setAutoCommit(false);
            try(PreparedStatement p=c.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")){p.setInt(1,user.getId());p.executeQuery().close();}
            try(PreparedStatement p=c.prepareStatement("SELECT id FROM web_attempts WHERE participant_id=? AND quiz_id=? AND result_id IS NULL ORDER BY started DESC")){
                p.setInt(1,user.getId());p.setInt(2,quizId);try(ResultSet r=p.executeQuery()){if(r.next())id=r.getString(1);}
            }
            if(id==null){id=UUID.randomUUID().toString();long started=System.currentTimeMillis();
                try(PreparedStatement p=c.prepareStatement("INSERT INTO web_attempts(id,participant_id,quiz_id,snapshot,answers,started,deadline) VALUES(?,?,?,?,?,?,?)")){
                    p.setString(1,id);p.setInt(2,user.getId());p.setInt(3,q.id);p.setString(4,Json.write(quiz(q,true)));p.setString(5,"{}");p.setLong(6,started);p.setLong(7,started+q.durationSeconds*1000L);p.executeUpdate();
                }
            }c.commit();
        }return access(user,id,null,false);
    }
    public static synchronized Map<String,Object> access(User user,String id,Map<String,Object> selection,boolean submit) throws SQLException {
        try(Connection c=Database.connect()){
            c.setAutoCommit(false);
            try(PreparedStatement p=c.prepareStatement("SELECT * FROM web_attempts WHERE id=? FOR UPDATE")){
                p.setString(1,id);try(ResultSet r=p.executeQuery()){
                    if(!r.next()||r.getInt("participant_id")!=user.getId()||!user.getRole().equals("Participant"))throw new IllegalArgumentException("You cannot access this attempt.");
                    Quiz q=decodeQuiz(Json.object(Json.parse(r.getString("snapshot"))));
                    Map<String,Object> saved=Json.object(Json.parse(r.getString("answers")));
                    Map<Integer,Integer> answers=new LinkedHashMap<>();for(var e:saved.entrySet())answers.put(Integer.parseInt(e.getKey()),((Number)e.getValue()).intValue());
                    long deadline=r.getLong("deadline"),started=r.getLong("started"),now=System.currentTimeMillis();
                    Integer resultId=(Integer)r.getObject("result_id");
                    if(resultId==null){
                        if(selection!=null&&now<deadline){int index=Json.number(selection,"question"),option=Json.number(selection,"option");
                            if(index<0||index>=q.questions.size()||option<0||option>3)throw new IllegalArgumentException("Invalid answer.");answers.put(index,option);
                        }
                        if(submit||now>=deadline)resultId=new ResultDAO().saveInTransaction(c,user,q,answers,(int)Math.min(q.durationSeconds,(now-started)/1000));
                        try(PreparedStatement update=c.prepareStatement("UPDATE web_attempts SET answers=?,result_id=? WHERE id=?")){
                            update.setString(1,Json.write(answers));if(resultId==null)update.setNull(2,Types.INTEGER);else update.setInt(2,resultId);update.setString(3,id);update.executeUpdate();
                        }
                    }
                    c.commit();Map<String,Object> out=new LinkedHashMap<>();out.put("id",id);out.put("quiz",quiz(q,false));out.put("answers",answers);out.put("deadline",deadline);out.put("serverNow",now);out.put("resultId",resultId);return out;
                }
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    public static void expire() throws SQLException {
        List<String> ids=new ArrayList<>();List<Integer> users=new ArrayList<>();
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("SELECT id,participant_id FROM web_attempts WHERE result_id IS NULL AND deadline<=?")){
            p.setLong(1,System.currentTimeMillis());try(ResultSet r=p.executeQuery()){while(r.next()){ids.add(r.getString(1));users.add(r.getInt(2));}}
        }
        for(int i=0;i<ids.size();i++)try(Connection c=Database.connect()){User u=findUser(c,users.get(i));if(u.getRole().equals("Participant"))access(u,ids.get(i),null,true);}
    }
    public static void requireNoActiveQuiz(int quizId) throws SQLException {
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM web_attempts WHERE quiz_id=? AND result_id IS NULL")){
            p.setInt(1,quizId);try(ResultSet r=p.executeQuery()){r.next();if(r.getInt(1)>0)throw new IllegalArgumentException("A participant is attempting this quiz. Wait until their attempt finishes.");}
        }
    }
    public static void requireNoActiveUser(int userId) throws SQLException {
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM web_attempts WHERE participant_id=? AND result_id IS NULL")){
            p.setInt(1,userId);try(ResultSet r=p.executeQuery()){r.next();if(r.getInt(1)>0)throw new IllegalArgumentException("This participant has an active attempt. Wait until it finishes.");}
        }
    }
    public static Map<String,Object> result(Attempt a){return Map.of("id",a.id,"participant",a.participant,"quizTitle",a.quizTitle,"score",a.score,"total",a.total,"seconds",a.seconds,"finishedAt",a.finishedAt,"percentage",a.percentage());}
    public static Map<String,Object> report(User user,int id) throws SQLException {
        Attempt a=new ResultDAO().findAll(user).stream().filter(v->v.id==id).findFirst().orElseThrow(()->new IllegalArgumentException("You cannot view this result."));
        Map<String,Object> out=new LinkedHashMap<>(result(a));List<Object> answers=new ArrayList<>();
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("SELECT * FROM answers WHERE attempt_id=? ORDER BY position")){
            p.setInt(1,id);try(ResultSet r=p.executeQuery()){while(r.next())answers.add(Map.of("text",r.getString("question_text"),"optionsText",r.getString("options_text"),"selectedAnswer",r.getInt("selected_answer"),"correctAnswer",r.getInt("correct_answer"),"explanation",r.getString("explanation")));}
        }out.put("answers",answers);return out;
    }
}
