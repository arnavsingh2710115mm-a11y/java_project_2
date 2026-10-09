package quiz;

import java.sql.SQLException;

/** Scheduled background work that submits overdue attempts without a browser open. */
public final class QuizMaintenanceService implements Runnable {
    @Override public void run() {
        try {
            // WebStore.access is synchronized and locks the attempt row in a transaction.
            WebStore.expire();
        } catch (SQLException | RuntimeException e) {
            // Handle failures so the scheduler can retry on its next run.
            System.err.println("Attempt maintenance failed: " + e.getClass().getSimpleName());
        }
    }
}
