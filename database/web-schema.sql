CREATE TABLE IF NOT EXISTS web_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    csrf VARCHAR(64) NOT NULL,
    expires BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS web_attempts (
    id VARCHAR(36) PRIMARY KEY,
    participant_id INTEGER NOT NULL REFERENCES users(id),
    quiz_id INTEGER NOT NULL REFERENCES quizzes(id),
    snapshot TEXT NOT NULL,
    answers TEXT NOT NULL,
    started BIGINT NOT NULL,
    deadline BIGINT NOT NULL,
    result_id INTEGER REFERENCES attempts(id)
);
CREATE INDEX IF NOT EXISTS web_attempt_deadline ON web_attempts(result_id,deadline);
