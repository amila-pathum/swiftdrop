CREATE TABLE dispatch_couriers (
    courier_id UUID PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'COURIER',
    available BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_dispatch_courier_role CHECK (role = 'COURIER')
);

CREATE TABLE dispatch_parcels (
    parcel_id UUID PRIMARY KEY,
    tracking_number VARCHAR(40) NOT NULL UNIQUE,
    pickup_area VARCHAR(120) NOT NULL,
    dropoff_area VARCHAR(120) NOT NULL,
    pickup_address TEXT NOT NULL,
    delivery_address TEXT NOT NULL,
    size VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    assigned_courier_id UUID REFERENCES dispatch_couriers(courier_id),
    charge NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (charge >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ,
    failure_reason VARCHAR(500),
    CONSTRAINT chk_dispatch_parcel_status CHECK (status IN
        ('CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','CANCELLED'))
);

CREATE INDEX idx_dispatch_unassigned ON dispatch_parcels(status, created_at)
    WHERE assigned_courier_id IS NULL;
CREATE INDEX idx_dispatch_parcel_courier_status ON dispatch_parcels(assigned_courier_id, status);
CREATE INDEX idx_dispatch_parcel_created ON dispatch_parcels(created_at);
CREATE INDEX idx_dispatch_parcel_status_created ON dispatch_parcels(status, created_at);
CREATE INDEX idx_dispatch_parcel_tracking ON dispatch_parcels(tracking_number);

CREATE TABLE dispatch_status_history (
    history_id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES dispatch_parcels(parcel_id),
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    changed_by VARCHAR(80),
    note VARCHAR(500)
);

CREATE INDEX idx_dispatch_history_parcel_time
    ON dispatch_status_history(parcel_id, changed_at);
CREATE INDEX idx_dispatch_history_status_time
    ON dispatch_status_history(new_status, changed_at);

CREATE TABLE dispatch_assignments (
    assignment_id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES dispatch_parcels(parcel_id),
    courier_id UUID NOT NULL REFERENCES dispatch_couriers(courier_id),
    previous_courier_id UUID REFERENCES dispatch_couriers(courier_id),
    assigned_by VARCHAR(80) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_dispatch_assignments_parcel_time
    ON dispatch_assignments(parcel_id, assigned_at);
CREATE INDEX idx_dispatch_assignments_courier_time
    ON dispatch_assignments(courier_id, assigned_at);

-- TODO: Replace these Dispatch-owned demo projections with Parcel and Courier
-- service APIs/events once those owner-owned contracts are implemented.
INSERT INTO dispatch_couriers (courier_id, display_name, available) VALUES
    ('10000000-0000-4000-8000-000000000001', 'Demo Courier A', TRUE),
    ('10000000-0000-4000-8000-000000000002', 'Demo Courier B', TRUE),
    ('10000000-0000-4000-8000-000000000003', 'Demo Courier C', FALSE);

INSERT INTO dispatch_parcels (
    parcel_id, tracking_number, pickup_area, dropoff_area, pickup_address, delivery_address,
    size, status, assigned_courier_id, charge, created_at, updated_at, delivered_at, failure_reason
) VALUES
    ('20000000-0000-4000-8000-000000000001', 'SD-DEMO-0001', 'Colombo', 'Jaffna', 'Colombo 03', 'Jaffna Town', 'MEDIUM', 'CREATED', NULL, 1250.00, now() - interval '1 day', now() - interval '1 day', NULL, NULL),
    ('20000000-0000-4000-8000-000000000002', 'SD-DEMO-0002', 'Colombo', 'Kandy', 'Colombo 05', 'Kandy Town', 'SMALL', 'CREATED', NULL, 850.00, now() - interval '4 hours', now() - interval '4 hours', NULL, NULL),
    ('20000000-0000-4000-8000-000000000003', 'SD-DEMO-0003', 'Galle', 'Colombo', 'Galle Fort', 'Colombo 07', 'LARGE', 'PICKED_UP', '10000000-0000-4000-8000-000000000001', 1450.00, now() - interval '2 days', now() - interval '1 day', NULL, NULL),
    ('20000000-0000-4000-8000-000000000004', 'SD-DEMO-0004', 'Colombo', 'Matara', 'Colombo 01', 'Matara Town', 'MEDIUM', 'IN_TRANSIT', '10000000-0000-4000-8000-000000000001', 1350.00, now() - interval '2 days', now() - interval '1 day', NULL, NULL),
    ('20000000-0000-4000-8000-000000000005', 'SD-DEMO-0005', 'Kandy', 'Colombo', 'Kandy Town', 'Colombo 04', 'SMALL', 'OUT_FOR_DELIVERY', '10000000-0000-4000-8000-000000000002', 900.00, now() - interval '3 days', now() - interval '2 hours', NULL, NULL),
    ('20000000-0000-4000-8000-000000000006', 'SD-DEMO-0006', 'Colombo', 'Galle', 'Colombo 02', 'Galle Town', 'MEDIUM', 'DELIVERED', '10000000-0000-4000-8000-000000000002', 1100.00, now() - interval '5 days', now() - interval '3 days', now() - interval '3 days', NULL),
    ('20000000-0000-4000-8000-000000000007', 'SD-DEMO-0007', 'Jaffna', 'Colombo', 'Jaffna Town', 'Colombo 06', 'LARGE', 'FAILED', '10000000-0000-4000-8000-000000000001', 1650.00, now() - interval '6 days', now() - interval '4 days', NULL, 'Recipient unavailable'),
    ('20000000-0000-4000-8000-000000000008', 'SD-DEMO-0008', 'Gampaha', 'Colombo', 'Gampaha Town', 'Colombo 08', 'SMALL', 'CREATED', NULL, 700.00, now() - interval '8 days', now() - interval '8 days', NULL, NULL);

INSERT INTO dispatch_status_history (history_id, parcel_id, previous_status, new_status, changed_at, changed_by, note) VALUES
    ('30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', NULL, 'CREATED', now() - interval '1 day', 'demo', 'Parcel created'),
    ('30000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000002', NULL, 'CREATED', now() - interval '4 hours', 'demo', 'Parcel created'),
    ('30000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000003', NULL, 'CREATED', now() - interval '2 days', 'demo', 'Parcel created'),
    ('30000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000003', 'CREATED', 'PICKED_UP', now() - interval '1 day', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000004', NULL, 'CREATED', now() - interval '2 days', 'demo', 'Parcel created'),
    ('30000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000004', 'CREATED', 'PICKED_UP', now() - interval '1 day 2 hours', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000004', 'PICKED_UP', 'IN_TRANSIT', now() - interval '1 day', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000008', '20000000-0000-4000-8000-000000000005', NULL, 'CREATED', now() - interval '3 days', 'demo', 'Parcel created'),
    ('30000000-0000-4000-8000-000000000009', '20000000-0000-4000-8000-000000000005', 'CREATED', 'PICKED_UP', now() - interval '2 days 4 hours', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000010', '20000000-0000-4000-8000-000000000005', 'PICKED_UP', 'IN_TRANSIT', now() - interval '1 day', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000011', '20000000-0000-4000-8000-000000000005', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', now() - interval '2 hours', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000012', '20000000-0000-4000-8000-000000000006', NULL, 'CREATED', now() - interval '5 days', 'demo', 'Parcel created'),
    ('30000000-0000-4000-8000-000000000013', '20000000-0000-4000-8000-000000000006', 'CREATED', 'PICKED_UP', now() - interval '4 days', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000014', '20000000-0000-4000-8000-000000000006', 'PICKED_UP', 'IN_TRANSIT', now() - interval '3 days 8 hours', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000015', '20000000-0000-4000-8000-000000000006', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', now() - interval '3 days 2 hours', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000016', '20000000-0000-4000-8000-000000000006', 'OUT_FOR_DELIVERY', 'DELIVERED', now() - interval '3 days', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000017', '20000000-0000-4000-8000-000000000007', NULL, 'CREATED', now() - interval '6 days', 'demo', 'Parcel created'),
    ('30000000-0000-4000-8000-000000000018', '20000000-0000-4000-8000-000000000007', 'CREATED', 'PICKED_UP', now() - interval '5 days', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000019', '20000000-0000-4000-8000-000000000007', 'PICKED_UP', 'IN_TRANSIT', now() - interval '4 days 8 hours', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000020', '20000000-0000-4000-8000-000000000007', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', now() - interval '4 days 2 hours', 'demo', NULL),
    ('30000000-0000-4000-8000-000000000021', '20000000-0000-4000-8000-000000000007', 'OUT_FOR_DELIVERY', 'FAILED', now() - interval '4 days', 'demo', 'Recipient unavailable'),
    ('30000000-0000-4000-8000-000000000022', '20000000-0000-4000-8000-000000000008', NULL, 'CREATED', now() - interval '8 days', 'demo', 'Parcel created');
