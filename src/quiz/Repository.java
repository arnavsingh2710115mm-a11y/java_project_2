package quiz;

import java.sql.SQLException;
import java.util.List;

// T is replaced by the type of record returned by a repository.
public interface Repository<T> {
    List<T> findAll(User viewer) throws SQLException;
}
