package quiz;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** An ID index for a refreshed view of records; SQL remains the persistent store. */
public final class DataRepository<T> {
    private final Map<Integer, T> records = new HashMap<>();

    public void put(int id, T record) {
        if (id <= 0) throw new IllegalArgumentException("Use the ID of a saved record.");
        records.put(id, Objects.requireNonNull(record));
    }
    public T findById(int id) { return records.get(id); }
    public int size() { return records.size(); }
    public void clear() { records.clear(); }
}
