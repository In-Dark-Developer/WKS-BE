CREATE TABLE result (
    id              UUID PRIMARY KEY,
    nickname        VARCHAR(20)  NOT NULL,
    birth_date      DATE         NOT NULL,
    birth_time      TIME,
    birth_region    VARCHAR(50),
    gender          VARCHAR(10)  NOT NULL,
    year_pillar     CHAR(2)      NOT NULL,
    month_pillar    CHAR(2)      NOT NULL,
    day_pillar      CHAR(2)      NOT NULL,
    hour_pillar     CHAR(2),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE reading (
    id              BIGSERIAL PRIMARY KEY,
    result_id       UUID         NOT NULL REFERENCES result(id) ON DELETE CASCADE,
    category        VARCHAR(20)  NOT NULL,
    score           SMALLINT     NOT NULL,
    content         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (result_id, category)
);

CREATE TABLE compatibility (
    id              BIGSERIAL PRIMARY KEY,
    origin_id       UUID         NOT NULL REFERENCES result(id) ON DELETE CASCADE,
    guest_id        UUID         NOT NULL REFERENCES result(id) ON DELETE CASCADE,
    score           SMALLINT     NOT NULL,
    tier            VARCHAR(10)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (origin_id, guest_id)
);

CREATE INDEX idx_compat_origin ON compatibility(origin_id);

CREATE TABLE signup (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    result_id       UUID         REFERENCES result(id) ON DELETE SET NULL,
    gender          VARCHAR(10)  NOT NULL,
    prefer_gender   VARCHAR(10)  NOT NULL,
    coupon_issued   BOOLEAN      NOT NULL DEFAULT false,
    verified_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE email_verification (
    token           VARCHAR(64)  PRIMARY KEY,
    signup_id       BIGINT       NOT NULL REFERENCES signup(id) ON DELETE CASCADE,
    expires_at      TIMESTAMPTZ  NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_verification_signup ON email_verification(signup_id);
