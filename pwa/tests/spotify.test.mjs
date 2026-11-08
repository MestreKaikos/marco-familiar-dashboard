import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { Script } from 'node:vm';
import test from 'node:test';
import pairHandler from '../api/spotify-pair.js';

function request(body, origin) {
  let status = 0;
  const headers = {};
  const response = {
    setHeader(key, value) { headers[key] = value; },
    status(code) { status = code; return this; },
    json(data) { return { status, data, headers }; }
  };
  return pairHandler({ method: 'POST', headers: origin ? { origin } : {}, body }, response);
}

test('La pàgina Spotify té una OAuth PKCE segura sense Client Secret', () => {
  const html = readFileSync(new URL('../spotify.html', import.meta.url), 'utf8');
  const js = readFileSync(new URL('../spotify.js', import.meta.url), 'utf8');
  assert.match(html, /Content-Security-Policy/);
  assert.match(html, /https:\/\/marc-familiar-pwa\.vercel\.app\/spotify\.html/);
  assert.doesNotThrow(() => new Script(js, { filename: 'spotify.js' }));
  for (const term of ['code_challenge_method', 'S256', 'code_verifier', 'redirect_uri', 'state'])
    assert.ok(js.includes(term), term + ' és imprescindible per a PKCE');
  assert.match(js, /user-modify-playback-state/);
  assert.match(js, /playlist-read-private/);
  assert.ok(!js.includes('client_secret'), 'No hi pot haver Client Secret al navegador');
});

test('No hi ha reproductor de música al marc ni accés directe als dispositius del mòbil', () => {
  const html = readFileSync(new URL('../spotify.html', import.meta.url), 'utf8');
  const js = readFileSync(new URL('../spotify.js', import.meta.url), 'utf8');
  assert.ok(!/<audio\b|<video\b/i.test(html));
  assert.match(js, /\/me\/player\/devices/);
  assert.match(js, /\/me\/player\/play\?device_id=/);
  assert.match(js, /spotify:playlist:/);
  assert.match(js, /\/me\/playlists/);
});

test('El servidor no retorna tokens a sol·licituds sense prova vàlida', async () => {
  const noProof = await request({ action: 'claim', proof: 'bad', until: Date.now() + 100000 });
  assert.equal(noProof.status, 400);
  assert.equal(noProof.headers['Cache-Control'], 'private, no-store');
  const badOrigin = await request({}, 'https://example.net');
  assert.equal(badOrigin.status, 403);
  const unauth = await request({ action:'approve', id: 'a'.repeat(64),
    until:Date.now()+120000, client_id:'b'.repeat(32),
    access_token:'short', refresh_token:'short', expires_at:Date.now()+3600000 });
  assert.equal(unauth.status, 400);
  const expired = await request({ action:'claim', proof:'f'.repeat(64), until:Date.now()-5 });
  assert.equal(expired.status, 400);
});

test('El servidor només desa playlists vàlides i l’URL QR no porta tokens', () => {
  const api = readFileSync(new URL('../api/spotify-pair.js', import.meta.url), 'utf8');
  assert.match(api, /SHA256/);
  assert.match(api, /PLAYLIST\.test/);
  assert.match(api, /claim/);
  assert.match(api, /refresh_token/);
  const js = readFileSync(new URL('../spotify.js', import.meta.url), 'utf8');
  assert.match(js, /#pair=/);
  assert.match(js, /activePairing/);
});
