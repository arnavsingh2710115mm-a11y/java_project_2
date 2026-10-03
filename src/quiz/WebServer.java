package quiz;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Java web entry point. Serves the UI and the authenticated JSON API together. */
public final class WebServer {
    private static final UserDAO users=new UserDAO();
    private static final QuizDAO quizzes=new QuizDAO();
    private static final long SESSION_MS=12*60*60*1000L;
    private static final Map<String,Window> rateWindows=new ConcurrentHashMap<>();
    private static String setupKey;
    private record Session(User user,String csrf){}
    private static class Window {long start=System.currentTimeMillis();int count;}
    private static class HttpError extends RuntimeException {final int code;HttpError(int c,String message){super(message);code=c;}}
    public static void main(String[] args) throws Exception {
        boolean production=Boolean.parseBoolean(System.getenv().getOrDefault("QUIZ_PRODUCTION","false"));
        if(production && (System.getenv("DATABASE_URL")==null||System.getenv("DATABASE_URL").isBlank()))
            throw new IllegalStateException("Production requires DATABASE_URL for shared PostgreSQL storage.");
        if(production && !publicUrl().startsWith("https://"))
            throw new IllegalStateException("Production requires the site's https PUBLIC_URL.");
        Database.initialize();WebStore.initialize();setupKey=System.getenv("QUIZ_SETUP_TOKEN");
        if(users.needsSetup()&&(setupKey==null||setupKey.length()<24)){
            if(production)throw new IllegalStateException("Set QUIZ_SETUP_TOKEN to a random value of at least 24 characters before first launch.");
            setupKey=random();System.out.println("First administrator setup key: "+setupKey);
        }
        int port=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
        HttpServer server=HttpServer.create(new InetSocketAddress("0.0.0.0",port),64);
        server.createContext("/",WebServer::handle);
        ThreadPoolExecutor executor=new ThreadPoolExecutor(4,16,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(100),new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(executor);server.start();
        ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(()->{try{WebStore.expire();}catch(Exception e){System.err.println("Attempt maintenance failed: "+e.getClass().getSimpleName());}},1,1,TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(()->{try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("DELETE FROM web_sessions WHERE expires<?")){p.setLong(1,System.currentTimeMillis());p.executeUpdate();}catch(Exception e){System.err.println("Session cleanup failed.");}rateWindows.entrySet().removeIf(e->System.currentTimeMillis()-e.getValue().start>600000);},1,10,TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(1);scheduler.shutdownNow();executor.shutdown();}));
        System.out.println("Quiz Platform web app: http://localhost:"+port);
    }
    private static void handle(HttpExchange x) throws IOException {
        Headers h=x.getResponseHeaders();h.set("X-Content-Type-Options","nosniff");h.set("Referrer-Policy","same-origin");h.set("X-Frame-Options","DENY");h.set("Cache-Control","no-store");
        h.set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
        if(production())h.set("Strict-Transport-Security","max-age=31536000");
        try{
            String path=x.getRequestURI().getPath(),method=x.getRequestMethod();
            if(path.equals("/health")){try(Connection c=Database.connect();Statement s=c.createStatement()){s.execute("SELECT 1");}json(x,200,Map.of("status","ok"));return;}
            if(!path.startsWith("/api/")){staticFile(x,path);return;}
            if(!method.equals("GET")){
                String origin=x.getRequestHeaders().getFirst("Origin");
                String expected=(publicUrl().isBlank()?"http://"+x.getRequestHeaders().getFirst("Host"):publicUrl()).replaceAll("/$","");
                if(origin!=null&&!origin.equals(expected))throw new HttpError(403,"This request came from a different website.");
                if(!Objects.toString(x.getRequestHeaders().getFirst("Content-Type"),"").startsWith("application/json"))throw new HttpError(415,"Send JSON content.");
                if("cross-site".equals(x.getRequestHeaders().getFirst("Sec-Fetch-Site")))throw new HttpError(403,"Cross-site request denied.");
            }
            Session session=session(x);User user=session==null?null:session.user;
            if(path.equals("/api/state")&&method.equals("GET")){
                Map<String,Object> data=new LinkedHashMap<>();data.put("setupRequired",users.needsSetup());data.put("user",user==null?null:WebStore.user(user));data.put("csrf",session==null?null:session.csrf);json(x,200,data);return;
            }
            if(path.equals("/api/setup")&&method.equals("POST")){
                limit(x,"setup",10);Map<String,Object> data=body(x);
                synchronized(users){if(!users.needsSetup())throw new HttpError(409,"Administrator setup is already complete.");
                    if(setupKey==null||!constant(setupKey,Json.string(data,"setupKey")))throw new HttpError(403,"The setup key is incorrect.");
                    validatePassword(data);user=users.setupAdmin(Json.string(data,"name"),Json.string(data,"email"),Json.string(data,"password"));}
                json(x,201,loginSession(x,user));return;
            }
            if(path.equals("/api/login")&&method.equals("POST")){
                limit(x,"login",40);Map<String,Object> data=body(x);validatePassword(data);String email=Json.string(data,"email");
                limitKey("login-email:"+hash(email.trim().toLowerCase(Locale.ROOT)),15);
                user=users.login(email,Json.string(data,"password"));json(x,200,loginSession(x,user));return;
            }
            if(path.equals("/api/register")&&method.equals("POST")){
                limit(x,"register",12);if(users.needsSetup())throw new HttpError(409,"The administrator must finish setup first.");Map<String,Object> data=body(x);validatePassword(data);
                user=users.register(Json.string(data,"name"),Json.string(data,"email"),Json.string(data,"password"),Json.string(data,"role"));json(x,201,loginSession(x,user));return;
            }
            if(user==null)throw new HttpError(401,"Please log in to continue.");
            if(!method.equals("GET")&&!constant(session.csrf,Objects.toString(x.getRequestHeaders().getFirst("X-CSRF-Token"),"")))throw new HttpError(403,"Your session needs refreshing. Reload the page and try again.");
            if(path.equals("/api/logout")&&method.equals("POST")){deleteSession(x);json(x,200,Map.of("ok",true));return;}
            String[] parts=path.split("/");
            if(path.equals("/api/quizzes")&&method.equals("GET")){
                List<Object> list=new ArrayList<>();for(Quiz q:quizzes.findAll(user)){
                    Map<String,Object> item=WebStore.quiz(q,false);try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM questions WHERE quiz_id=?")){p.setInt(1,q.id);try(ResultSet r=p.executeQuery()){r.next();item.put("questionCount",r.getInt(1));}}
                    list.add(item);
                }json(x,200,list);return;
            }
            if(path.equals("/api/quizzes")&&method.equals("POST")){
                Map<String,Object> data=body(x);data.put("id",0);data.put("creatorId",user.getId());data.put("status","Draft");Quiz q=WebStore.decodeQuiz(data);quizzes.save(user,q);json(x,201,WebStore.quiz(q,true));return;
            }
            if(parts.length>=4&&parts[2].equals("quizzes")){
                int id=Integer.parseInt(parts[3]);
                if(parts.length==4){
                    if(method.equals("GET")){json(x,200,WebStore.quiz(quizzes.load(user,id),!user.getRole().equals("Participant")));return;}
                    if(method.equals("PUT")){WebStore.requireNoActiveQuiz(id);Map<String,Object> data=body(x);data.put("id",id);data.put("creatorId",user.getId());data.put("status","Draft");Quiz q=WebStore.decodeQuiz(data);quizzes.save(user,q);json(x,200,WebStore.quiz(q,true));return;}
                    if(method.equals("DELETE")){WebStore.requireNoActiveQuiz(id);quizzes.delete(user,id);json(x,200,Map.of("ok",true));return;}
                }
                if(parts.length==5&&method.equals("POST")){
                    switch(parts[4]){
                        case "submit" -> {quizzes.submit(user,id);json(x,200,Map.of("ok",true));return;}
                        case "review" -> {Map<String,Object>d=body(x);String decision=Json.string(d,"decision");if(!List.of("approve","reject").contains(decision))throw new IllegalArgumentException("Select approve or reject.");String note=Json.string(d,"note");if(decision.equals("reject")&&note.isBlank())throw new IllegalArgumentException("Please explain why the quiz is rejected.");quizzes.review(user,id,decision.equals("approve"),note);json(x,200,Map.of("ok",true));return;}
                        case "start" -> {json(x,201,WebStore.start(user,id));return;}
                    }
                }
            }
            if(parts.length>=4&&parts[2].equals("attempts")){
                String id=parts[3];
                if(parts.length==4&&method.equals("GET")){json(x,200,WebStore.access(user,id,null,false));return;}
                if(parts.length==5&&method.equals("POST")&&parts[4].equals("answer")){json(x,200,WebStore.access(user,id,body(x),false));return;}
                if(parts.length==5&&method.equals("POST")&&parts[4].equals("finish")){json(x,200,WebStore.access(user,id,null,true));return;}
            }
            if(path.equals("/api/results")&&method.equals("GET")){json(x,200,new ResultDAO().findAll(user).stream().map(WebStore::result).toList());return;}
            if(parts.length==4&&parts[2].equals("results")&&method.equals("GET")){json(x,200,WebStore.report(user,Integer.parseInt(parts[3])));return;}
            if(path.equals("/api/users")){
                if(method.equals("GET")){json(x,200,users.findAll(user).stream().map(WebStore::user).toList());return;}
                if(method.equals("POST")){Map<String,Object>d=body(x);validatePassword(d);User created=users.create(user,Json.string(d,"name"),Json.string(d,"email"),Json.string(d,"password"),Json.string(d,"role"));json(x,201,WebStore.user(created));return;}
            }
            if(parts.length==4&&parts[2].equals("users")){
                int id=Integer.parseInt(parts[3]);UserDAO.requireAdmin(user);WebStore.requireNoActiveUser(id);
                if(method.equals("PUT")){Map<String,Object>d=body(x);users.update(user,id,Json.string(d,"name"),Json.string(d,"email"),Json.string(d,"role"));json(x,200,Map.of("ok",true));return;}
                if(method.equals("DELETE")){users.delete(user,id);json(x,200,Map.of("ok",true));return;}
            }
            throw new HttpError(404,"This page or action was not found.");
        }catch(HttpError e){json(x,e.code,Map.of("error",e.getMessage()));}
        catch(IllegalArgumentException e){json(x,400,Map.of("error",Objects.toString(e.getMessage(),"Invalid request.")));}
        catch(SQLException e){String state=Objects.toString(e.getSQLState(),"");String message=state.equals("23505")?"That email or record already exists.":state.startsWith("23")?"This record has linked quizzes or results and cannot be deleted.":"The database is temporarily unavailable. Please try again.";json(x,state.startsWith("23")?409:503,Map.of("error",message));System.err.println("Database error state: "+state);}
        catch(Exception e){System.err.println("Request failed: "+e.getClass().getSimpleName());json(x,500,Map.of("error","The request could not be completed. Please try again."));}
        finally{x.close();}
    }
    private static Map<String,Object> body(HttpExchange x)throws IOException {byte[] bytes=x.getRequestBody().readNBytes(1_048_577);if(bytes.length>1_048_576)throw new HttpError(413,"This form is too large.");return Json.object(Json.parse(new String(bytes,StandardCharsets.UTF_8)));}
    private static void validatePassword(Map<String,Object>d){String p=Json.string(d,"password");if(p.length()<8||p.length()>256)throw new IllegalArgumentException("Password must contain 8 to 256 characters.");}
    private static Session session(HttpExchange x)throws SQLException {
        String token=cookie(x);if(token==null)return null;
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("SELECT user_id,csrf FROM web_sessions WHERE token_hash=? AND expires>?")){
            p.setString(1,hash(token));p.setLong(2,System.currentTimeMillis());try(ResultSet r=p.executeQuery()){if(!r.next())return null;return new Session(WebStore.findUser(c,r.getInt(1)),r.getString(2));}
        }
    }
    private static Map<String,Object> loginSession(HttpExchange x,User user)throws SQLException {
        deleteSession(x);String token=random(),csrf=random();
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("INSERT INTO web_sessions(token_hash,user_id,csrf,expires) VALUES(?,?,?,?)")){p.setString(1,hash(token));p.setInt(2,user.getId());p.setString(3,csrf);p.setLong(4,System.currentTimeMillis()+SESSION_MS);p.executeUpdate();}
        x.getResponseHeaders().set("Set-Cookie","quiz_session="+token+"; Path=/; HttpOnly; SameSite=Lax; Max-Age=43200"+(production()?"; Secure":""));
        return Map.of("user",WebStore.user(user),"csrf",csrf);
    }
    private static void deleteSession(HttpExchange x)throws SQLException {String token=cookie(x);if(token!=null)try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("DELETE FROM web_sessions WHERE token_hash=?")){p.setString(1,hash(token));p.executeUpdate();}x.getResponseHeaders().set("Set-Cookie","quiz_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0"+(production()?"; Secure":""));}
    private static String cookie(HttpExchange x){String header=x.getRequestHeaders().getFirst("Cookie");if(header==null)return null;for(String item:header.split(";")){String[]p=item.trim().split("=",2);if(p.length==2&&p[0].equals("quiz_session"))return p[1];}return null;}
    private static String publicUrl(){return System.getenv().getOrDefault("PUBLIC_URL",System.getenv().getOrDefault("RENDER_EXTERNAL_URL",""));}
    private static boolean production(){return Boolean.parseBoolean(System.getenv().getOrDefault("QUIZ_PRODUCTION","false"));}
    private static String random(){byte[]bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean constant(String a,String b){return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    private static void limit(HttpExchange x,String action,int max){limitKey(action+":"+x.getRemoteAddress().getAddress().getHostAddress(),max);}
    private static void limitKey(String key,int max){if(rateWindows.size()>10000)throw new HttpError(429,"Please try again later.");Window w=rateWindows.computeIfAbsent(key,k->new Window());synchronized(w){if(System.currentTimeMillis()-w.start>300000){w.start=System.currentTimeMillis();w.count=0;}if(++w.count>max)throw new HttpError(429,"Too many attempts. Please wait five minutes.");}}
    private static void json(HttpExchange x,int status,Object value)throws IOException {byte[]bytes=Json.write(value).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);}
    private static void staticFile(HttpExchange x,String path)throws IOException {
        if(!x.getRequestMethod().equals("GET")&&!x.getRequestMethod().equals("HEAD"))throw new HttpError(405,"Method not allowed.");
        String file= switch(path){case "/app.js"->"app.js";case "/style.css"->"style.css";case "/favicon.svg"->"favicon.svg";default->"index.html";};
        byte[]bytes=Files.readAllBytes(Path.of("web",file));String type=file.endsWith(".js")?"text/javascript":file.endsWith(".css")?"text/css":file.endsWith(".svg")?"image/svg+xml":"text/html";
        x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");if(x.getRequestMethod().equals("HEAD")){x.sendResponseHeaders(200,-1);return;}x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);
    }
}
