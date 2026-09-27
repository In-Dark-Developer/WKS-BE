CREATE TABLE dating_photo (
    id UUID PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    object_key VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE dating_profile (
    id UUID PRIMARY KEY,
    member_id BIGINT NOT NULL UNIQUE REFERENCES member(id) ON DELETE CASCADE,
    result_id UUID NOT NULL UNIQUE REFERENCES result(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    contact_method VARCHAR(10) NOT NULL,
    contact_value VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    mbti VARCHAR(4) NOT NULL,
    bio VARCHAR(500) NOT NULL,
    photo_id UUID NOT NULL UNIQUE REFERENCES dating_photo(id),
    verified_at TIMESTAMPTZ,
    matched_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE dating_recommendation (
    id BIGSERIAL PRIMARY KEY,
    viewer_member_id BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    candidate_profile_id UUID NOT NULL REFERENCES dating_profile(id) ON DELETE CASCADE,
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (viewer_member_id, candidate_profile_id)
);

CREATE INDEX idx_dating_recommendation_active
    ON dating_recommendation(viewer_member_id) WHERE active;
