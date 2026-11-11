import { createHash } from 'node:crypto';

const CLIENT_ID = '9b638e8c035e4d1fae936f20cee31c8f';
const PAIR_KEY = 'marc:spotify:pair:';
const HASH = /^[a-f0-9]{64}$/;
const CLIENT = /^[a-zA-Z0-9]{32}$/;
const LIMIT = 5 * 60_000;
const json = (res, status, data) => res.status(status).json(data);
const sha256 = value => createHash('sha256').update(value).digest('hex');
async function kv(command) {
  const url = process.env.KV_REST_API_URL || process.env.UPSTASH_REDIS_REST_URL;
  const token = process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN;
  if (!url || !token) throw new Error('El servidor de vinculació Spotify encara no està configurat.');
  const response = await fetch(url, { method: 'POST', headers: {
    Authorization: 'Bearer ' + token, 'Content-Type': 'application/json'
  }, body: JSON.stringify(command), signal: AbortSignal.timeout(8000) });
  if (!response.ok) throw new Error('No es pot accedir a la vinculació segura.');
  const data = await response.json();
  if (data.error) throw new Error('Error de vinculació segura.');
  return data.result;
}
export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  if (req.method !== 'POST') return json(res, 405, { error: 'Mètode no admès.' });
  try {
    const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
    if (!body || typeof body !== 'object') return json(res, 400, {error:'Dades no vàlides.'});
    if (body.action === 'publish') {
      const { pair, until, client_id, access_token, refresh_token, expires_at } = body;
      if (!HASH.test(pair) || !Number.isSafeInteger(until) || until < Date.now() ||
          until > Date.now() + LIMIT || client_id !== CLIENT_ID ||
          !CLIENT.test(client_id) || typeof access_token !== 'string' ||
          access_token.length < 12 || access_token.length > 4096 ||
          typeof refresh_token !== 'string' || refresh_token.length < 12 ||
          refresh_token.length > 4096 || !Number.isSafeInteger(expires_at) ||
          expires_at <= Date.now()) return json(res,400,{error:'Autorització no vàlida.'});
      // Verify the token with Spotify: do not accept arbitrary unverified credentials.
      const check = await fetch('https://api.spotify.com/v1/me', {
        headers:{Authorization:'Bearer '+access_token},signal:AbortSignal.timeout(8000)});
      if (!check.ok) return json(res, 401, {error:'Spotify ha rebutjat l’autorització.'});
      const ttl = Math.max(1,Math.ceil((until-Date.now())/1000));
      const payload = JSON.stringify({client_id,access_token,refresh_token,expires_at});
      const result = await kv(['SET',PAIR_KEY+pair,payload,'EX',ttl,'NX']);
      if (result !== 'OK') return json(res,409,{error:'Aquest QR ja s’ha utilitzat.'});
      return json(res,200,{ok:true});
    }
    if (body.action === 'claim') {
      const {proof,until} = body;
      if (typeof proof !== 'string' || !HASH.test(proof) ||
          !Number.isSafeInteger(until) || until < Date.now() ||
          until > Date.now()+LIMIT) return json(res,400,{error:'QR no vàlid o caducat.'});
      const pair = sha256(proof);
      const stored = await kv(['GETDEL',PAIR_KEY+pair]);
      if (!stored) return json(res,200,{linked:false});
      const parsed=JSON.parse(stored);
      return json(res,200,{linked:true,...parsed});
    }
    return json(res,400,{error:'Acció no vàlida.'});
  } catch (error) {
    return json(res,503,{error:error.message || 'Error de vinculació.'});
  }
}
