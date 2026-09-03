const { test } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { percentile, digest, phase, createClient, redisDeltas, discoverIds } = require('./cache-performance.cjs');

test('discovery uses stable primary-key ordering and fills a distinct-ID sample', async () => {
  const urls = [];
  const result = await discoverIds(async route => {
    urls.push(route);
    const url = new URL(route, 'http://localhost');
    assert.equal(url.searchParams.get('sortBy'), 'id');
    assert.equal(url.searchParams.get('sortDirection'), 'ASC');
    const page = Number(url.searchParams.get('page'));
    return { content: (page === 0 ? [1, 2] : [2, 3]).map(id => ({ id })), totalPages: 2, totalElements: 3 };
  }, '/api/jobs', 3);
  assert.deepEqual(result.ids, [1, 2, 3]);
  assert.equal(result.totalElements, 3);
  assert.equal(urls.length, 2);
});

test('Redis phase deltas exclude inspection hits and misses', () => {
  assert.deepEqual(redisDeltas({ hits: 100, misses: 10 }, { hits: 116, misses: 13, selectedKeys: 2, before: 2, present: 2 }), { hits: 10, misses: 3 });
  assert.deepEqual(redisDeltas({ hits: 100, misses: 10 }, { hits: 100, misses: 14, selectedKeys: 2, before: 0, present: 0 }), { hits: 0, misses: 0 });
});

test('nearest-rank percentiles handle tails, single samples, and empty data', () => {
  const values = Array.from({ length: 100 }, (_, i) => 100 - i);
  assert.equal(percentile(values, .5), 50);
  assert.equal(percentile(values, .95), 95);
  assert.equal(percentile(values, .99), 99);
  assert.equal(percentile([17], .99), 17);
  assert.equal(percentile([], .95), null);
  assert.equal(values[0], 100);
});

test('response fingerprints ignore object key order but detect content changes', () => {
  assert.equal(digest({ id: 1, detail: { a: 1, b: 2 } }), digest({ detail: { b: 2, a: 1 }, id: 1 }));
  assert.notEqual(digest({ id: 1, title: 'old' }), digest({ id: 1, title: 'new' }));
});

test('arrival scheduler maintains concurrent fixed-rate requests and catches bad IDs and stale payloads', async () => {
  let active = 0, peak = 0;
  const read = async id => {
    peak = Math.max(peak, ++active);
    await new Promise(resolve => setTimeout(resolve, 70));
    active--;
    return { status: 200, durationMs: 70, json: { id, title: 'same' } };
  };
  const first = await phase('synthetic first', [1, 2], 6, 50, read);
  assert.equal(first.summary.requests + first.summary.dropped, 6);
  assert.equal(first.summary.errors, 0);
  assert.ok(peak > 1, 'fixed arrival requests should overlap');
  const hashes = new Map(first.samples.map(x => [x.id, x.sha256]));
  const bad = await phase('synthetic changed', [1, 2], 2, 50,
    async id => ({ status: 200, durationMs: 2, json: { id: id === 1 ? 999 : id, title: 'changed' } }), hashes);
  assert.equal(bad.summary.errors, 2);
  assert.equal(bad.summary.p50Ms, null);
});

test('HTTP client parses responses and sends bearer credentials without saving them', async t => {
  const server = http.createServer((req, res) => {
    assert.equal(req.headers.authorization, 'Bearer test-only');
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ id: 42 }));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const client = createClient(`http://127.0.0.1:${server.address().port}`, () => 'test-only');
  t.after(() => { client.close(); server.close(); });
  const response = await client.request('/api/jobs/42');
  assert.equal(response.status, 200);
  assert.equal(response.json.id, 42);
  assert.ok(response.durationMs >= 0);
});
