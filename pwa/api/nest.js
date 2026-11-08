import { createHash, createHmac, timingSafeEqual } from 'node:crypto';

const API = 'https://smartdevicemanagement.googleapis.com/v1';
const DEVICES = { timbre: 'sdm.devices.types.DOORBELL', camera: 'sdm.devices.types.CAMERA' };
const COMMAND = 'sdm.devices.commands.CameraLiveStream.';
let cached;

function equal(a, b) {
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
}

function signedSession(key, device, id) {
  const payload = Buffer.from(JSON.stringify({ device, id, until: Date.now() + 300000 })).toString('base64url');
  return payload + '.' + createHmac('sha256', key).update(payload).digest('base64url');
}

function readSession(key, value) {
  if (typeof value !== 'string' || value.length > 4096) return null;
  const [payload, signature, extra] = value.split('.');
  if (!payload || !signature || extra !== undefined) return null;
  if (!equal(signature, createHmac('sha256', key).update(payload).digest('base64url'))) return null;
  const session = JSON.parse(Buffer.from(payload, 'base64url').toString());
  return session.until > Date.now() ? session : null;
}

async function access(config) {
  if (cached?.until > Date.now()) return cached.token;
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    body: new URLSearchParams({
      client_id: config.client_id, client_secret: config.client_secret,
      refresh_token: config.refresh_token, grant_type: 'refresh_token',
    }),
    signal: AbortSignal.timeout(8000),
  });
  if (!response.ok) throw new Error('Autorització Google (' + response.status + ')');
  const token = await response.json();
  if (!token.access_token) throw new Error('Resposta d’autorització incompleta');
  cached = { token: token.access_token, until: Date.now() + Math.max(0, Number(token.expires_in) - 60) * 1000 };
  return cached.token;
}

/**
 * Tradueix exclusivament errors SDP coneguts de Google. No enviem mai
 * missatges arbitraris del servidor ni l'SDP original al dispositiu.
 */
export function safeNestOfferReason(rawMessage) {
  const message = typeof rawMessage === 'string' ? rawMessage.slice(0, 1000) : '';
  if (/CRLF|newline|new.line|final.*line/i.test(message))
    return 'falta el salt de línia al final de la petició SDP';
  if (/m.lines|media lines|each of audio, video and application|audio.*video.*application/i.test(message))
    return 'calen tres canals SDP en ordre: àudio, vídeo i dades';
  if (/recvonly|receive.only|audio.*receive/i.test(message))
    return 'el canal d’àudio ha de tenir a=recvonly';
  if (/opus/i.test(message))
    return 'el canal d’àudio requereix el còdec Opus';
  if (/invalid offer|invalid.*sdp|sdp.*invalid/i.test(message))
    return 'el format SDP no és vàlid; comprova els canals a la pantalla';
  return 'Google ha rebutjat l’oferta WebRTC (400); comprova els canals a la pantalla';
}

async function google(token, path, command, params) {
  const headers = { Authorization: 'Bearer ' + token };
  const options = { headers, signal: AbortSignal.timeout(10000) };
  if (command) {
    headers['Content-Type'] = 'application/json';
    options.method = 'POST';
    options.body = JSON.stringify({ command: COMMAND + command, params });
  }
  const response = await fetch(API + '/' + path, options);
  if (!response.ok) {
    if (command === 'GenerateWebRtcStream' && response.status === 400) {
      const details = await response.json().catch(() => ({}));
      throw new Error('Oferta Nest: ' + safeNestOfferReason(details?.error?.message));
    }
    throw new Error('Google Nest (' + response.status + ')');
  }
  return response.json();
}

async function pair(body, config, res, authorized) {
  const lifetime = body.until - Date.now();
  const approving = body.action === 'pair-approve';
  const value = approving ? body.id : body.proof;
  if (!Number.isSafeInteger(body.until) || lifetime <= 0 || lifetime > 360000 ||
      typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) {
    return res.status(400).json({ error: 'El QR ha caducat o no és vàlid. Obre’n un de nou al marc.' });
  }
  if (approving && !authorized) return res.status(401).json({ error: 'La clau de càmeres és incorrecta.' });

  const id = approving ? value : createHash('sha256').update(value).digest('hex');
  const entry = createHmac('sha256', config.key).update(id + ':' + body.until).digest('hex');
  const { getCache } = await import('@vercel/functions');
  const cache = getCache({ namespace: 'marc-familiar-nest-pair' });
  if (approving) {
    await cache.set(entry, true, { ttl: Math.ceil(lifetime / 1000) });
    return res.status(200).json({ ok: true });
  }
  if (await cache.get(entry) !== true) return res.status(200).json({ waiting: true });
  await cache.delete(entry);
  return res.status(200).json({ key: config.key });
}

export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'private, no-store');
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return res.status(405).json({ error: 'Cal una petició POST.' });
  }

  let config;
  try {
    config = JSON.parse(process.env.NEST_CONFIG || '{}');
    if (!config || !/^[a-f0-9-]{36}$/i.test(config.project_id ?? '') ||
        !/^[a-f0-9]{64}$/i.test(config.key ?? '') ||
        !config.client_id || !config.client_secret || !config.refresh_token) {
      return res.status(503).json({ error: 'La connexió Nest encara no està configurada.' });
    }
  } catch {
    return res.status(503).json({ error: 'La configuració Nest no és vàlida.' });
  }

  const auth = req.headers.authorization;
  const authorized = typeof auth === 'string' && equal(auth, 'Bearer ' + config.key);

  let body;
  try {
    body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
    if (!body || typeof body !== 'object' || Array.isArray(body)) throw new Error();
  } catch {
    return res.status(400).json({ error: 'Petició incorrecta.' });
  }

  try {
    if (body.action === 'pair-approve' || body.action === 'pair-claim') {
      return await pair(body, config, res, authorized);
    }
    if (!authorized) return res.status(401).json({ error: 'La clau de càmeres és incorrecta.' });

    const prefix = 'enterprises/' + config.project_id + '/devices';
    if (body.action === 'stop') {
      const session = readSession(config.key, body.session);
      if (!session || !session.device.startsWith(prefix + '/')) {
        return res.status(400).json({ error: 'Sessió de vídeo no vàlida o caducada.' });
      }
      await google(await access(config), session.device + ':executeCommand',
        'StopWebRtcStream', { mediaSessionId: session.id });
      return res.status(200).json({ ok: true });
    }

    if (body.action !== 'start' || !Object.hasOwn(DEVICES, body.device) ||
        typeof body.sdp !== 'string' || body.sdp.length > 65536 || !body.sdp.startsWith('v=0')) {
      return res.status(400).json({ error: 'Petició de vídeo incorrecta.' });
    }

    const token = await access(config);
    const devices = await google(token, prefix);
    const matches = (devices.devices ?? []).filter(device =>
      device.type === DEVICES[body.device] && device.name.startsWith(prefix + '/'));
    if (matches.length !== 1) {
      return res.status(409).json({ error: 'Google no ha retornat un únic dispositiu d’aquest tipus.' });
    }
    const device = matches[0].name;
    const { results } = await google(token, device + ':executeCommand',
      'GenerateWebRtcStream', { offerSdp: body.sdp });
    return res.status(200).json({
      answerSdp: results.answerSdp,
      session: signedSession(config.key, device, results.mediaSessionId),
    });
  } catch (error) {
    const message = /^(Autorització Google|Google Nest) \(\d{3}\)$/.test(error.message)
      ? error.message + '.' : error.message.startsWith('Oferta Nest: ')
        ? error.message : 'No s’ha pogut completar la connexió amb Google Nest.';
    return res.status(502).json({ error: message });
  }
}
