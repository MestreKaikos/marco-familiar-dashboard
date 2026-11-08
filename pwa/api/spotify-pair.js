/**
 * Vinculació temporal entre el telèfon (OAuth PKCE Spotify) i el Marc Familiar.
 * No es desa cap credencial duradora a Vercel: el frame la reclama amb una
 * prova secreta de 256 bits i la conserva a l'emmagatzematge privat Android.
 */
import { createHash, timingSafeEqual } from 'node:crypto';
import { getCache } from '@vercel/functions';

const MAX_LIFETIME_MS = 300000;
const NAMESPACE = 'marc-familiar-spotify-pair';
const SHA256 = /^[0-9a-f]{64}$/;
const CLIENT_ID = /^[a-zA-Z0-9]{32}$/;
const PLAYLIST = /^spotify:playlist:[A-Za-z0-9]+$/;

function reply(res, code, body) {
  res.setHeader('Cache-Control', 'private, no-store');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  return res.status(code).json(body);
}
function isValidExpiry(until) {
  return Number.isSafeInteger(until) &&
    until > Date.now() && until - Date.now() <= MAX_LIFETIME_MS;
}
function keyFor(id, until) {
  return createHash('sha256').update('spotify-pair:' + id + ':' + until).digest('hex');
}
function validOrigin(headers) {
  if (!headers.origin) return true; // L'APK envia peticions nadiues, sense Origin.
  return headers.origin === 'https://marc-familiar-pwa.vercel.app';
}
function parseBody(body) {
  if (typeof body === 'string') return JSON.parse(body);
  return body;
}

export const config = { api: { bodyParser: { sizeLimit: '16kb' } } };

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return reply(res, 405, { error: 'Només peticions POST.' });
  }
  if (!validOrigin(req.headers ?? {}))
    return reply(res, 403, { error: 'Origen no autoritzat.' });
  let body;
  try {
    body = parseBody(req.body);
    if (!body || typeof body !== 'object' || Array.isArray(body)) throw new Error();
  } catch { return reply(res, 400, { error: 'Petició invàlida.' }); }

  try {
    if (body.action === 'approve') {
      const { id, until, client_id, access_token, refresh_token, expires_at, favourites } = body;
      if (!SHA256.test(id ?? '') || !isValidExpiry(until) ||
          !CLIENT_ID.test(client_id ?? '') ||
          typeof access_token !== 'string' || access_token.length < 12 || access_token.length > 4096 ||
          typeof refresh_token !== 'string' || refresh_token.length < 12 || refresh_token.length > 4096 ||
          !Number.isSafeInteger(expires_at) || expires_at <= Date.now() ||
          expires_at > Date.now() + 7200000)
        return reply(res, 400, { error: 'La vinculació o la sessió Spotify no és vàlida.' });

      const cache = getCache({ namespace: NAMESPACE });
      const key = keyFor(id, until);
      const exists = await cache.get(key);
      if (exists === 'claimed')
        return reply(res, 409, { error: 'Aquest QR ja s’ha utilitzat.' });
      await cache.set(key, { client_id, access_token, refresh_token, expires_at,
        favourites: Array.isArray(favourites) ? favourites.filter(x => typeof x === 'string' && PLAYLIST.test(x)).slice(0, 30) : [] },
        { ttl: Math.max(1, Math.ceil((until - Date.now()) / 1000)) });
      return reply(res, 200, { ok: true });
    }
    if (body.action === 'claim') {
      const { proof, until } = body;
      if (!SHA256.test(proof ?? '') || !isValidExpiry(until))
        return reply(res, 400, { error: 'QR invàlid o caducat.' });
      const id = createHash('sha256').update(proof).digest('hex');
      const cache = getCache({ namespace: NAMESPACE });
      const key = keyFor(id, until);
      const data = await cache.get(key);
      if (!data || data === 'claimed') return reply(res, 200, { waiting: true });
      await cache.set(key, 'claimed', { ttl: 30 });
      return reply(res, 200, {
        linked: true,
        client_id: data.client_id, access_token: data.access_token,
        refresh_token: data.refresh_token, expires_at: data.expires_at,
        favourites: data.favourites || []
      });
    }
    return reply(res, 400, { error: 'Acció de vinculació desconeguda.' });
  } catch {
    return reply(res, 503, { error: 'El servei temporal de vinculació no està disponible.' });
  }
}