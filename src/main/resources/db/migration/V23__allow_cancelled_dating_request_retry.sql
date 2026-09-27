ALTER TABLE dating_request DROP CONSTRAINT dating_request_status_check;
ALTER TABLE dating_request ADD CONSTRAINT dating_request_status_check
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'));

DROP INDEX uq_dating_request_pair;
CREATE UNIQUE INDEX uq_dating_request_pair ON dating_request (
    LEAST(sender_profile_id, recipient_profile_id),
    GREATEST(sender_profile_id, recipient_profile_id)
) WHERE status <> 'CANCELLED';
