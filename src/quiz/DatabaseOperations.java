package quiz;

import java.sql.SQLException;

/** Generic CRUD contract implemented by a DAO, with reads supplied by Repository. */
public interface DatabaseOperations<T> extends Repository<T> {
    void save(User actor, T record) throws SQLException, QuizNotFoundException;
    void update(User actor, T record) throws SQLException, QuizNotFoundException;
    void delete(User actor, int id) throws SQLException, QuizNotFoundException;
}
