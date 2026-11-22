-- Parcels assigned before parcel.delivery-snapshot.v1 existed never sent their
-- delivery details to the Courier Service, so couriers saw only the tracking
-- number and status. Queue one snapshot for each assigned parcel; the outbox
-- publisher delivers them like any other event. Same JSON shape as
-- ParcelDeliverySnapshotEvent.
INSERT INTO parcel_event_outbox (event_id, parcel_id, routing_key, payload, occurred_at)
SELECT snapshot.event_id, snapshot.parcel_id, 'parcel.delivery-snapshot.v1',
       jsonb_build_object(
           'eventId', snapshot.event_id,
           'eventType', 'ParcelDeliverySnapshot',
           'occurredAt', to_char(snapshot.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
           'parcelId', snapshot.parcel_id,
           'trackingNumber', snapshot.tracking_number,
           'pickupAddress', snapshot.pickup_address,
           'deliveryAddress', snapshot.delivery_address,
           'receiverName', snapshot.receiver_name,
           'receiverPhone', snapshot.receiver_phone,
           'weight', snapshot.weight,
           'size', snapshot.size,
           'merchantUserId', snapshot.merchant_id
       )::text,
       snapshot.occurred_at
FROM (
    SELECT gen_random_uuid() AS event_id, p.id AS parcel_id, p.tracking_number, p.pickup_address,
           p.delivery_address, p.receiver_name, p.receiver_phone, p.weight, p.size, p.merchant_id,
           now() AS occurred_at
    FROM parcels p
    WHERE p.assigned_courier_id IS NOT NULL
) snapshot;
