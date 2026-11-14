CREATE TABLE couriers (
    courier_id UUID PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    available BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO couriers (courier_id, display_name, active, available) VALUES
    ('10000000-0000-4000-8000-000000000001', 'Demo Courier A', TRUE, TRUE),
    ('10000000-0000-4000-8000-000000000002', 'Demo Courier B', TRUE, TRUE),
    ('10000000-0000-4000-8000-000000000003', 'Demo Courier C', TRUE, FALSE);
