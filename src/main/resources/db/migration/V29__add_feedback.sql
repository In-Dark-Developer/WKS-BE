CREATE TABLE feedback (
    id          UUID          PRIMARY KEY,
    content     VARCHAR(2000) NOT NULL CHECK (char_length(content) BETWEEN 1 AND 2000),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
