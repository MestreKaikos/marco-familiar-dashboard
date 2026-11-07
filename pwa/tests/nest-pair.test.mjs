import assert from 'node:assert/strict';
import { createHash, randomBytes } from 'node:crypto';
import test from 'node:test';
import handler from '../api/nest.js';

const key = 'a'.repeat(64);
process.env.NEST_CONFIG = JSON.stringify({
  project_id: 'c43cc1e5-6c43-4be6-9d0b-be1c841edc08',
  client_id: 'test', client_secret: 'test', refresh_token: 'test', key
});

async function post(body, authorization) {
  let status;
  return handler({ method: 'POST', headers: { authorization }, body }, {
    setHeader(name, value) {
      assert.equal(name, 'Cache-Control');
      assert.equal(value, 'private, no-store');
    },
    status(code) { status = code; return this; },
    json(data) { return { status, data }; }
  });
}

test('només el marc que té la prova privada pot reclamar una autorització aprovada', async () => {
  const proof = randomBytes(32).toString('hex');
  const id = createHash('sha256').update(proof).digest('hex');
  const until = Date.now() + 300000;
  const approve = { action: 'pair-approve', id, until };
  const claim = { action: 'pair-claim', proof, until };

  assert.deepEqual(await post(claim), { status: 200, data: { waiting: true } });
  assert.equal((await post(approve, 'Bearer incorrecta')).status, 401);
  assert.deepEqual(await post(approve, 'Bearer ' + key), { status: 200, data: { ok: true } });
  assert.deepEqual(await post({ ...claim, proof: id }), { status: 200, data: { waiting: true } });
  assert.deepEqual(await post(claim), { status: 200, data: { key } });
  assert.deepEqual(await post(claim), { status: 200, data: { waiting: true } });
  assert.equal((await post({ ...claim, until: Date.now() - 1000 })).status, 400);
});
