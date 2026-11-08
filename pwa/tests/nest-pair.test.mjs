import assert from 'node:assert/strict';
import { createHash, randomBytes } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { Script } from 'node:vm';
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

test('El reproductor de Nest és compatible amb Unified Plan a WebView 74', () => {
  const html = readFileSync(new URL('../nest.html', import.meta.url), 'utf8');
  assert.match(html, /sdpSemantics:\s*'unified-plan'/);
  const p = html.indexOf("peer.addTransceiver('audio'");
  const q = html.indexOf("peer.addTransceiver('video'");
  const r = html.indexOf("peer.createDataChannel('data'");
  assert.ok(p >= 0 && p < q && q < r,
    'Nest exigeix audio, video i application en aquest ordre');
  assert.match(html, /setTimeout\(resolve, 8000\)/);
  assert.match(html, /id="diagnostic"/, 'El marc necessita diagnòstic visible');
  assert.match(html, /report\(\)/, 'Diagnòstic del motor i estats WebRTC');
  for (const phase of ['WebRTC configurat', 'demanant vídeo a Google', 'resposta de vídeo aplicada'])
    assert.ok(html.includes(phase), 'Ha de mostrar la fase: ' + phase);
  assert.ok(!html.includes("mark('nest',result.answerSdp)"),
    'No s\'ha de mostrar cap dada privada de la resposta SDP');
  for (const [, code] of html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)) {
    assert.doesNotThrow(() => new Script(code));
  }
});
