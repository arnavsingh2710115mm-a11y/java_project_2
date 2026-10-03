package quiz;

import java.util.*;

/** Small JSON codec so the web layer needs no extra library. */
public final class Json {
    private Json() {}
    public static Object parse(String source) {
        Parser p = new Parser(source); Object value = p.value(0); p.space();
        if (p.pos != source.length()) throw new IllegalArgumentException("Invalid JSON.");
        return value;
    }
    public static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) {
            StringBuilder b = new StringBuilder("\"");
            for (char c : s.toCharArray()) switch (c) {
                case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
                default -> { if (c < 32) b.append(String.format("\\u%04x", (int)c)); else b.append(c); }
            }
            return b.append('"').toString();
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?,?> map) {
            List<String> parts = new ArrayList<>();
            map.forEach((k,v) -> parts.add(write(k.toString()) + ":" + write(v)));
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Iterable<?> list) {
            List<String> parts = new ArrayList<>(); list.forEach(v -> parts.add(write(v)));
            return "[" + String.join(",", parts) + "]";
        }
        if (value instanceof Object[] a) return write(Arrays.asList(a));
        throw new IllegalArgumentException("Unsupported JSON value.");
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> object(Object v) {
        if (!(v instanceof Map)) throw new IllegalArgumentException("Expected an object.");
        return (Map<String,Object>) v;
    }
    public static String string(Map<String,Object> m, String key) {
        if (!(m.get(key) instanceof String s)) throw new IllegalArgumentException("Missing field: " + key);
        return s;
    }
    public static int number(Map<String,Object> m, String key) {
        if (!(m.get(key) instanceof Number n) || n.doubleValue() != n.intValue())
            throw new IllegalArgumentException("Invalid number: " + key);
        return n.intValue();
    }
    private static class Parser {
        final String s; int pos;
        Parser(String s) { this.s=s; }
        void space() { while(pos<s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }
        boolean consume(char c) { space(); if(pos<s.length() && s.charAt(pos)==c) {pos++;return true;} return false; }
        Object value(int depth) {
            if(depth>40) throw new IllegalArgumentException("JSON nesting limit exceeded.");
            space(); if(pos>=s.length()) throw new IllegalArgumentException("Incomplete JSON.");
            char c=s.charAt(pos);
            if(c=='"') return string();
            if(consume('{')) { Map<String,Object> m=new LinkedHashMap<>(); if(consume('}'))return m;
                do { space(); String k=string(); if(!consume(':'))fail(); m.put(k,value(depth+1)); } while(consume(','));
                if(!consume('}'))fail(); return m; }
            if(consume('[')) { List<Object> a=new ArrayList<>(); if(consume(']'))return a;
                do {a.add(value(depth+1));}while(consume(',')); if(!consume(']'))fail();return a; }
            for(String token:List.of("true","false","null")) if(s.startsWith(token,pos)) {pos+=token.length();return token.equals("null")?null:token.equals("true");}
            int start=pos; if(pos<s.length()&&s.charAt(pos)=='-')pos++;
            while(pos<s.length()&&Character.isDigit(s.charAt(pos)))pos++;
            if(pos<s.length()&&s.charAt(pos)=='.'){pos++;while(pos<s.length()&&Character.isDigit(s.charAt(pos)))pos++;}
            if(pos<s.length()&&(s.charAt(pos)=='e'||s.charAt(pos)=='E')){pos++;if(pos<s.length()&&(s.charAt(pos)=='+'||s.charAt(pos)=='-'))pos++;while(pos<s.length()&&Character.isDigit(s.charAt(pos)))pos++;}
            try{return new java.math.BigDecimal(s.substring(start,pos));}catch(Exception e){throw new IllegalArgumentException("Invalid JSON number.");}
        }
        String string(){
            if(!consume('"')){fail();}StringBuilder b=new StringBuilder();
            while(pos<s.length()){char c=s.charAt(pos++);if(c=='"')return b.toString();if(c<32)fail();
                if(c=='\\'){if(pos>=s.length())fail();c=s.charAt(pos++);switch(c){
                    case '"','\\','/' -> b.append(c);case 'b' -> b.append('\b');case 'f' -> b.append('\f');case 'n' -> b.append('\n');case 'r' -> b.append('\r');case 't' -> b.append('\t');
                    case 'u' -> {if(pos+4>s.length())fail();try{b.append((char)Integer.parseInt(s.substring(pos,pos+4),16));}catch(Exception e){fail();}pos+=4;}
                    default -> fail();}
                }else b.append(c);
            }throw new IllegalArgumentException("Unterminated JSON string.");
        }
        void fail(){throw new IllegalArgumentException("Invalid JSON.");}
    }
}
