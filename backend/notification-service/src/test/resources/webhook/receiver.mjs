import { createHmac, timingSafeEqual } from 'node:crypto'
import { createServer as httpServer } from 'node:http'
import { createServer as httpsServer } from 'node:https'
import { readFileSync } from 'node:fs'
import { createInterface } from 'node:readline'
import { pathToFileURL } from 'node:url'

// Local demo only. Accepted event IDs are remembered until this process stops.
export function createReceiver({ secret, now = () => Date.now() / 1000, onAccepted = () => {}, tls } = {}) {
  if (typeof secret !== 'string' || !secret.trim()) throw new Error('WEBHOOK_SIGNING_SECRET is required')
  const accepted = new Set()
  let remainingFailures = 0
  function reply(response, status) {
    response.writeHead(status).end()
  }
  const handler = async (request, response) => {
    if (request.url !== '/webhook') { reply(response, 404); return }
    if (request.method !== 'POST') { reply(response, 405); return }
    try {
      const chunks = []
      let size = 0
      for await (const chunk of request) {
        size += chunk.length
        if (size > 65536) { reply(response, 413); return }
        chunks.push(chunk)
      }
      const body = Buffer.concat(chunks)
      const timestamp = request.headers['x-swiftdrop-timestamp']
      const signature = request.headers['x-swiftdrop-signature']
      if (typeof timestamp !== 'string' || !/^\d{1,13}$/.test(timestamp)
          || Math.abs(now() - Number(timestamp)) > 300
          || typeof signature !== 'string' || !/^sha256=[a-f0-9]{64}$/.test(signature)) {
        reply(response, 401); return
      }
      const expected = createHmac('sha256', secret).update(timestamp + '.').update(body).digest()
      if (!timingSafeEqual(expected, Buffer.from(signature.slice(7), 'hex'))) { reply(response, 401); return }
      let payload
      try { payload = JSON.parse(body.toString('utf8')) } catch { reply(response, 400); return }
      const eventId = request.headers['x-swiftdrop-event-id']
      if (!payload || Array.isArray(payload) || typeof payload !== 'object'
          || typeof eventId !== 'string' || !eventId || eventId.length > 100 || payload.eventId !== eventId) {
        reply(response, 400); return
      }
      if (accepted.has(eventId)) { reply(response, 204); return }
      if (remainingFailures > 0) { remainingFailures--; reply(response, 503); return }
      // Only these fields may reach the console; contacts, addresses and secrets are excluded.
      const summary = Object.fromEntries(['eventId', 'trackingNumber', 'newStatus', 'occurredAt']
        .filter((key) => typeof payload[key] === 'string').map((key) => [key, payload[key]]))
      onAccepted(summary)
      accepted.add(eventId)
      reply(response, 204)
    } catch {
      if (!response.headersSent && !response.destroyed) reply(response, 400)
    }
  }
  const server = tls ? httpsServer(tls, handler) : httpServer(handler)
  server.requestTimeout = 5000
  server.headersTimeout = 5000
  server.setTimeout(5000, (socket) => socket.destroy())
  return {
    server,
    failNext(count) {
      if (!Number.isSafeInteger(count) || count < 0) throw new Error('Use a non-negative integer')
      remainingFailures = count
    },
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const cert = process.env.WEBHOOK_DEMO_CERT
    const key = process.env.WEBHOOK_DEMO_KEY
    if (Boolean(cert) !== Boolean(key)) throw new Error('Set both WEBHOOK_DEMO_CERT and WEBHOOK_DEMO_KEY')
    const port = Number(process.env.WEBHOOK_DEMO_PORT || 9090)
    if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid WEBHOOK_DEMO_PORT')
    const receiver = createReceiver({
      secret: process.env.WEBHOOK_SIGNING_SECRET,
      tls: cert ? { cert: readFileSync(cert), key: readFileSync(key) } : undefined,
      onAccepted: (summary) => console.log(JSON.stringify(summary)),
    })
    receiver.server.on('error', () => { console.error('Unable to start the demo receiver. Check the port and TLS files.'); process.exitCode = 1 })
    receiver.server.listen(port, '127.0.0.1', () => {
      console.log(`Demo receiver: ${cert ? 'https' : 'http'}://127.0.0.1:${port}/webhook`)
      console.log('Commands: fail 2 (fail the next two new valid deliveries), success, quit')
    })
    const input = createInterface({ input: process.stdin })
    input.on('line', (line) => {
      const command = line.trim()
      if (command === 'quit') { receiver.server.close(); input.close(); return }
      if (command === 'success') { receiver.failNext(0); console.log('Failure simulation cleared.'); return }
      if (/^fail \d+$/.test(command)) {
        try { receiver.failNext(Number(command.slice(5))); console.log('Failure simulation enabled.'); return } catch { /* show help */ }
      }
      console.log('Use: fail <count>, success, or quit')
    })
  } catch (error) {
    // Configuration errors do not print environment values or arbitrary exception text.
    console.error('Unable to configure receiver. Set the signing secret, valid port and optional matching TLS files.')
    process.exitCode = 1
  }
}
