"""Dashboard instructions grounded in the corresponding frontend pages."""

DISPATCH_GUIDE = """
Open Dispatch and select Waiting parcels to find a parcel awaiting assignment.
Choose its assignment action, select a Courier and confirm with Assign.
Use Active assignments to review assignments, Ongoing deliveries to follow
deliveries, Courier workload to see workloads, and Reports for dispatch reports.
"""

ROLE_GUIDES = {
    "CUSTOMER": """
To book a new delivery, open Create Delivery in the sidebar. Enter the pickup
address and choose the pickup city. Select Continue. Enter the receiver's name,
phone, delivery address and city, then select Continue. Choose the service type,
enter the weight in kg and select the parcel size. Once the fee estimate appears,
select Continue, review the details and select Confirm Delivery. The confirmation
shows the tracking number and final charge.
To find a previous booking or its tracking number, open My Deliveries and
select View Details on the delivery. To track by number, enter it in Tracking
number on the Dashboard and select Track Parcel.
To cancel your own delivery, open My Deliveries, find the parcel and select
Cancel in its row. The Cancel button is available only while the status is
Created. Selecting it sends the cancellation request immediately. After a
successful cancellation the status is Cancelled.
""",
    "MERCHANT": """
Merchant orders are created through the merchant API from the business's backend.
Open Orders to search or filter existing orders and view their details.
For an approved business, open API keys, select Generate New API Key, enter a
key name and select Generate Key. Use Webhooks for webhook settings and
Business profile for the merchant profile.
""",
    "COURIER": """
Open Today for today's work. Open Deliveries to find an assigned parcel,
select Open Delivery and use the available action for its current status.
Open Profile for the courier profile.
""",
    "DISPATCHER": DISPATCH_GUIDE,
    "ADMIN": """
Open User Management for user accounts. Open Merchant Approvals or Courier
Approvals to review the corresponding applications. Open Delivery Zones to
manage delivery areas or Pricing to manage fees.
""" + DISPATCH_GUIDE,
}
