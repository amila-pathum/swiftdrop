import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createHmac } from 'node:crypto'
import { createReceiver } from './receiver.mjs'

const secret = 'test-receiver-secret'
const timestamp = 1800000000
async function withReceiver(run) {
  const received = []
  const receiver = createReceiver({ secret, now: () => timestamp, onAccepted: (summary) => received.push(summary) })
  await new Promise((resolve) => receiver.server.listen(0, '127.0.0.1', resolve))
  const url = `http://127.0.0.1:${receiver.server.address().port}/webhook`
  async function send({ id = 'event-1', value, stamp = timestamp, signingSecret = secret, headers = {} } = {}) {
    const body = value ?? JSON.stringify({ eventId: id, trackingNumber: 'SD-1', newStatus: 'DELIVERED', occurredAt: '2026-11-14T00:00:00Z', phone: 'private', address: 'private' })
    const signature = 'sha256=' + createHmac('sha256', signingSecret).update(stamp + '.').update(body).digest('hex')
    return fetch(url, { method: 'POST', body, headers: {
      'X-SwiftDrop-Event-Id': id, 'X-SwiftDrop-Timestamp': String(stamp), 'X-SwiftDrop-Signature': signature, ...headers,
    } })
  }
  try { await run({ receiver, received, send, url }) }
  finally { receiver.server.closeAllConnections(); await new Promise((resolve) => receiver.server.close(resolve)) }
}

test('independently signed webhook succeeds and summary excludes contact information', () => withReceiver(async ({ send, received }) => {
  assert.equal((await send()).status, 204)
  assert.deepEqual(Object.keys(received[0]).sort(), ['eventId', 'trackingNumber', 'newStatus', 'occurredAt'].sort())
}))
test('fails twice then succeeds, and suppresses accepted duplicates', () => withReceiver(async ({ send, received, receiver }) => {
  receiver.failNext(2)
  assert.equal((await send()).status, 503)
  assert.equal((await send()).status, 503)
  assert.equal((await send()).status, 204)
  receiver.failNext(1)
  assert.equal((await send()).status, 204)
  assert.equal((await send({ id: 'new-event' })).status, 503)
  assert.equal(received.length, 1)
}))
test('rejects invalid signatures and timestamps without consuming simulated failures', () => withReceiver(async ({ send, received, receiver }) => {
  receiver.failNext(1)
  for (const args of [
    { signingSecret: 'wrong' }, { stamp: timestamp - 301 }, { stamp: timestamp + 301 },
    { headers: { 'X-SwiftDrop-Timestamp': 'invalid' } }, { headers: { 'X-SwiftDrop-Signature': 'sha256=abc' } },
  ]) assert.equal((await send(args)).status, 401)
  assert.equal((await send()).status, 503)
  assert.equal(received.length, 0)
}))
test('rejects signed malformed payloads, mismatching IDs and oversized bodies', () => withReceiver(async ({ send }) => {
  for (const value of ['broken', 'null', '[]', '{}', '{"eventId":"other"}']) assert.equal((await send({ value })).status, 400)
  assert.equal((await send({ value: 'x'.repeat(65537) })).status, 413)
}))
test('local failure control supports returning immediately to success', () => withReceiver(async ({ send, receiver, url }) => {
  receiver.failNext(10); receiver.failNext(0)
  assert.equal((await send()).status, 204)
  assert.throws(() => receiver.failNext(-1))
  assert.equal((await fetch(url)).status, 405)
  assert.equal((await fetch(url + '/admin', { method: 'POST' })).status, 404)
}))
test('requires an explicit signing secret', () => {
  assert.throws(() => createReceiver())
  assert.throws(() => createReceiver({ secret: '' }))
})
