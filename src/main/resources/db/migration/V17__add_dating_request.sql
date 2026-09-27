CREATE TABLE dating_request (
    id UUID PRIMARY KEY,
    sender_profile_id UUID NOT NULL REFERENCES dating_profile(id) ON DELETE CASCADE,
    recipient_profile_id UUID NOT NULL REFERENCES dating_profile(id) ON DELETE CASCADE,
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CHECK (sender_profile_id <> recipient_profile_id)
);

CREATE UNIQUE INDEX uq_dating_request_pair ON dating_request (
    LEAST(sender_profile_id, recipient_profile_id),
    GREATEST(sender_profile_id, recipient_profile_id)
);

CREATE INDEX idx_dating_request_sender ON dating_request(sender_profile_id, created_at DESC);
CREATE INDEX idx_dating_request_recipient ON dating_request(recipient_profile_id, created_at DESC);
