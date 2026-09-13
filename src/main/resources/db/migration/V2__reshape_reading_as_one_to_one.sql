DROP TABLE reading;

CREATE TABLE reading (
    result_id          UUID         PRIMARY KEY REFERENCES result(id) ON DELETE CASCADE,
    destiny_title      VARCHAR(100) NOT NULL,
    destiny_content    TEXT         NOT NULL,
    marriage_grade     VARCHAR(10)  NOT NULL,
    marriage_content   TEXT         NOT NULL,
    children_grade     VARCHAR(10)  NOT NULL,
    children_content   TEXT         NOT NULL,
    love_grade         VARCHAR(10)  NOT NULL,
    love_content       TEXT         NOT NULL,
    lucky_item         VARCHAR(100) NOT NULL,
    lucky_place        VARCHAR(100) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
