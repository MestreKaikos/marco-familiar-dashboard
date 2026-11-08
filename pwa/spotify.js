/* Marc Familiar · Spotify Connect sense reproductor al marc.
 * OAuth Authorization Code + PKCE al telèfon; accés a l'API des de l'Android natiu.
 */
'use strict';
const REDIRECT = 'https://marc-familiar-pwa.vercel.app/spotify.html';
const SCOPES = [
  'playlist-read-private', 'playlist-read-collaborative',
  'user-read-playback-state', 'user-modify-playback-state', 'user-read-private'
].join(' ');
const el = id => document.getElementById(id);
const text = (id, value) => { el(id).textContent = value; };
const setStatus = (value, error = false) => {
  text('status', value);
  el('status').classList.toggle('error', error);
};
let clientId = localStorage.getItem('marc_spotify_client_id') || '';
let credentials = null;
let allPlaylists = [];
let devices = [];
let selectedDevice = '';
let favorites = new Set(JSON.parse(localStorage.getItem('marc_spotify_favorites') || '[]')
  .filter(uri => /^spotify:playlist:[A-Za-z0-9]+$/.test(uri)));

const pairPattern = /^([a-f0-9]{64})\.(\d{13})$/;
function activePairing() {
  const saved = sessionStorage.getItem('marc_spotify_pair');
  const parsed = pairPattern.exec(saved || '');
  return parsed && Number(parsed[2]) > Date.now()
    ? { id: parsed[1], until: Number(parsed[2]) } : null;
}
function toggleView(connected) {
  el('setup').hidden = connected;
  el('connected').hidden = !connected;
  el('pairing').hidden = !connected || !activePairing();
}
function saveClientId(value) {
  clientId = value.trim();
  if (/^[a-zA-Z0-9]{32}$/.test(clientId))
    localStorage.setItem('marc_spotify_client_id', clientId);
}
const randomHex = bytes => Array.from(bytes).map(v => v.toString(16).padStart(2, '0')).join('');
function randomVerifier() {
  const bytes = new Uint8Array(64);
  crypto.getRandomValues(bytes);
  return randomHex(bytes);
}
async function challenge(verifier) {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
    .replaceAll('+','-').replaceAll('/','_').replace(/=+$/, '');
}
async function authorize() {
  saveClientId(el('clientId').value);
  if (!/^[a-zA-Z0-9]{32}$/.test(clientId)) {
    setStatus('Introdueix el Client ID de 32 caràcters del teu projecte Spotify.', true);
    return;
  }
  const verifier = randomVerifier();
  const state = randomHex(crypto.getRandomValues(new Uint8Array(24)));
  sessionStorage.setItem('marc_spotify_verifier', verifier);
  sessionStorage.setItem('marc_spotify_state', state);
  const query = new URLSearchParams({
    response_type: 'code', client_id: clientId, scope: SCOPES,
    redirect_uri: REDIRECT, state, code_challenge_method: 'S256',
    code_challenge: await challenge(verifier)
  });
  location.assign('https://accounts.spotify.com/authorize?' + query);
}
async function exchangeToken(fields) {
  const response = await fetch('https://accounts.spotify.com/api/token', {
    method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: new URLSearchParams(fields), cache:'no-store'
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || !data.access_token)
    throw Error('Spotify no ha autoritzat la sessió (' + (data.error || response.status) + ').');
  return data;
}
function storeCredentials(data) {
  credentials = {
    access_token: data.access_token,
    refresh_token: data.refresh_token || (credentials && credentials.refresh_token),
    expires_at: Date.now() + (Number(data.expires_in) || 3600) * 1000
  };
  if (!credentials.refresh_token) throw Error('Falta el token de renovació de Spotify.');
  sessionStorage.setItem('marc_spotify_session', JSON.stringify(credentials));
}
async function ensureToken() {
  if (!credentials) throw Error('Vincula Spotify de nou.');
  if (credentials.expires_at > Date.now() + 60_000) return credentials.access_token;
  const data = await exchangeToken({
    grant_type: 'refresh_token', refresh_token: credentials.refresh_token, client_id: clientId
  });
  storeCredentials(data);
  return credentials.access_token;
}
async function spotify(path, method = 'GET', body) {
  const token = await ensureToken();
  const response = await fetch('https://api.spotify.com/v1' + path, {
    method, headers: {
      Authorization: 'Bearer ' + token,
      ...(body ? {'Content-Type': 'application/json'} : {})
    },
    ...(body ? {body: JSON.stringify(body)} : {}),
    cache:'no-store'
  });
  if (response.status === 204) return {};
  if (!response.ok) {
    if (response.status === 401) {
      credentials.expires_at = 0;
      await ensureToken();
      // Reintent únic; no reproduïm un altre cop si ha estat acceptat.
      return spotifyAfterRefresh(path, method, body);
    }
    const note = response.status === 403
      ? 'Spotify Premium o permisos de reproducció insuficients.'
      : response.status === 404
        ? 'No trobo l’altaveu. Inicia Spotify des del mòbil al Nest i actualitza els dispositius.'
        : response.status === 429
          ? 'Spotify limita temporalment les peticions. Torna-ho a provar més endavant.'
          : 'Spotify ha retornat l’error ' + response.status + '.';
    throw Error(note);
  }
  return response.json();
}
async function spotifyAfterRefresh(path, method, body) {
  const response = await fetch('https://api.spotify.com/v1' + path, {
    method, cache:'no-store',
    headers: {
      Authorization: 'Bearer ' + credentials.access_token,
      ...(body ? {'Content-Type':'application/json'} : {})
    },
    ...(body ? {body:JSON.stringify(body)} : {})
  });
  if (response.status === 204) return {};
  if (!response.ok) throw Error('Spotify ha retornat l’error ' + response.status + '.');
  return response.json();
}
function makeButton(label, onClick, secondary = false) {
  const button = document.createElement('button');
  button.textContent = label;
  button.className = secondary ? 'secondary small' : 'primary small';
  button.addEventListener('click', onClick);
  return button;
}
function validPlaylist(item) {
  return item && typeof item.id === 'string' &&
    /^[a-zA-Z0-9]+$/.test(item.id);
}
function drawPlaylists() {
  const list = el('playlists');
  const fav = el('favourites');
  list.replaceChildren();
  fav.replaceChildren();
  const query = el('searchLists').value.trim().toLocaleLowerCase('ca');
  const selected = allPlaylists.filter(p => favorites.has(p.uri));
  if (!selected.length) {
    const p = document.createElement('p');
    p.className = 'muted'; p.textContent = 'Marca amb ★ les playlists que vols tenir a mà al marc.';
    fav.append(p);
  }
  const card = (p, target) => {
    const row = document.createElement('div'); row.className = 'item';
    const desc = document.createElement('div'); desc.className = 'desc';
    const name = document.createElement('p'); name.textContent = p.name;
    const meta = document.createElement('span'); meta.className = 'meta';
    meta.textContent = p.ownerName || 'Playlist';
    desc.append(name, meta);
    const star = makeButton(favorites.has(p.uri) ? '★' : '☆', () => {
      if (favorites.has(p.uri)) favorites.delete(p.uri); else favorites.add(p.uri);
      localStorage.setItem('marc_spotify_favorites', JSON.stringify([...favorites]));
      drawPlaylists();
    }, true);
    star.setAttribute('aria-label', favorites.has(p.uri) ? 'Treure dels favorits' : 'Afegir als favorits');
    const play = makeButton('▶', () => playPlaylist(p));
    play.setAttribute('aria-label', 'Reproduir ' + p.name);
    row.append(desc, star, play);target.append(row);
  };
  selected.forEach(p => card(p, fav));
  allPlaylists.filter(p => !favorites.has(p.uri) && p.name.toLocaleLowerCase('ca').includes(query))
    .forEach(p => card(p, list));
}
async function loadPlaylists() {
  const entries = [];
  for (let offset = 0; offset < 200; offset += 50) {
    const data = await spotify('/me/playlists?limit=50&offset=' + offset);
    if (!Array.isArray(data.items)) break;
    entries.push(...data.items.filter(validPlaylist).map(p => ({
      id: p.id, uri: 'spotify:playlist:' + p.id,
      name: (p.name || 'Playlist sense nom').slice(0, 130),
      ownerName: p.owner && p.owner.display_name
    })));
    if (entries.length >= (data.total || Infinity) || data.items.length < 50) break;
  }
  allPlaylists = entries;
  drawPlaylists();
  setStatus(entries.length + ' playlists disponibles.');
}
async function loadDevices() {
  const data = await spotify('/me/player/devices');
  devices = (data.devices || []).filter(d => d.id && !d.is_restricted);
  const select = el('devices');
  const before = select.value || selectedDevice;
  select.replaceChildren();
  const placeholder = document.createElement('option');
  placeholder.value = ''; placeholder.textContent = 'Tria un altaveu…';
  select.append(placeholder);
  devices.forEach(d => {
    const option = document.createElement('option');
    option.value = d.id;
    option.textContent = d.name + (d.is_active ? ' · actiu' : '');
    select.append(option);
  });
  if (devices.some(d => d.id === before)) select.value = before;
  else if (devices.length === 1) select.value = devices[0].id;
  selectedDevice = select.value;
  text('now', devices.length ? 'Selecciona el Nest o el grup d’altaveus al menú.' :
    'No apareix cap dispositiu. Obre Spotify al mòbil i activa-hi el Nest o el grup.');
}
function deviceId() {
  const id = el('devices').value;
  if (!id || !devices.some(d => d.id === id)) {
    throw Error('Tria primer un Nest o grup disponible a Spotify Connect.');
  }
  return id;
}
async function playPlaylist(p) {
  try {
    setStatus('Enviant «' + p.name + '» als altaveus…');
    await spotify('/me/player/play?device_id=' + encodeURIComponent(deviceId()),
      'PUT', {context_uri: p.uri});
    setStatus('Sonant al dispositiu seleccionat: ' + p.name);
    text('now', '▶ ' + p.name);
  } catch (error) { setStatus(error.message, true); }
}
async function command(action) {
  try {
    const endpoint = action === 'pause' ? 'pause'
      : action === 'previous' ? 'previous'
      : action === 'next' ? 'next' : 'play';
    await spotify('/me/player/' + endpoint + '?device_id=' + encodeURIComponent(deviceId()),
      action === 'pause' || action === 'resume' ? 'PUT' : 'POST',
      action === 'resume' ? {} : undefined);
    setStatus('Comanda enviada al dispositiu seleccionat.');
  } catch (error) { setStatus(error.message, true); }
}
async function approvePairing() {
  const pair = activePairing();
  if (!pair) {setStatus('El QR ha caducat. Genera’n un de nou al marc.', true);return;}
  if (!credentials || !credentials.refresh_token) {
    setStatus('Primer has de connectar Spotify.', true);return;
  }
  try {
    await ensureToken();
    const response = await fetch('/api/spotify-pair', {
      method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({
        action:'approve', id:pair.id, until:pair.until, client_id:clientId,
        access_token:credentials.access_token,
        refresh_token:credentials.refresh_token,
        expires_at:credentials.expires_at,
        favourites:[...favorites].slice(0,30)
      }),
      cache:'no-store'
    });
    const result = await response.json();
    if (!response.ok) throw Error(result.error || 'No s’ha pogut vincular el marc.');
    sessionStorage.removeItem('marc_spotify_pair');
    el('pairing').hidden = true;
    setStatus('Marc autoritzat! Ja pots controlar Spotify des de la pantalla de Casa.');
  } catch (error) { setStatus(error.message, true); }
}
async function initialize() {
  el('clientId').value = clientId;
  el('connect').addEventListener('click', authorize);
  el('disconnect').addEventListener('click', () => {
    credentials = null;
    sessionStorage.removeItem('marc_spotify_session');
    toggleView(false);
    setStatus('Sessió del mòbil tancada. El marc manté la seva autorització.');
  });
  el('reloadDevices').addEventListener('click', () => loadDevices().catch(e=>setStatus(e.message,true)));
  el('reloadLists').addEventListener('click', () => loadPlaylists().catch(e=>setStatus(e.message,true)));
  el('searchLists').addEventListener('input', drawPlaylists);
  el('devices').addEventListener('change', () => {selectedDevice = el('devices').value;});
  ['pause','resume','previous','next'].forEach(c =>
    el(c).addEventListener('click', () => command(c)));
  el('approvePair').addEventListener('click', approvePairing);
  el('cancelPair').addEventListener('click', () => {
    sessionStorage.removeItem('marc_spotify_pair');el('pairing').hidden=true;
  });

  const fromQr = location.hash.match(/^#pair=([a-f0-9]{64}\.\d{13})$/);
  if (fromQr) {
    sessionStorage.setItem('marc_spotify_pair', fromQr[1]);
    history.replaceState({}, '', REDIRECT);
  }
  const query = new URLSearchParams(location.search);
  if (query.has('error')) {
    history.replaceState({}, '', REDIRECT);
    setStatus('Spotify ha rebutjat l’autorització: ' + query.get('error'), true);
  }
  if (query.has('code')) {
    const code = query.get('code');
    history.replaceState({}, '', REDIRECT);
    const state = sessionStorage.getItem('marc_spotify_state');
    const verifier = sessionStorage.getItem('marc_spotify_verifier');
    if (!state || query.get('state') !== state || !verifier || !clientId) {
      setStatus('La sessió OAuth no coincideix. Torna a connectar Spotify.', true);
      return;
    }
    sessionStorage.removeItem('marc_spotify_state');
    sessionStorage.removeItem('marc_spotify_verifier');
    try {
      const tokens = await exchangeToken({
        grant_type:'authorization_code',code,redirect_uri:REDIRECT,
        client_id:clientId,code_verifier:verifier
      });
      storeCredentials(tokens);
    } catch (error) {
      setStatus(error.message,true);return;
    }
  } else {
    try {
      const saved = JSON.parse(sessionStorage.getItem('marc_spotify_session') || 'null');
      if (saved && saved.refresh_token && saved.access_token && saved.expires_at)
        credentials = saved;
    } catch { /* la sessió del mòbil es pot descartar */ }
  }
  toggleView(Boolean(credentials));
  if (credentials) {
    setStatus('Spotify connectat. Carregant playlists i dispositius…');
    try {
      await Promise.all([loadPlaylists(),loadDevices()]);
      const profile = await spotify('/me');
      text('account', 'Compte: ' + (profile.display_name || profile.id || 'Spotify Premium'));
    } catch(error) {setStatus(error.message,true);}
  }
}
initialize().catch(error=>setStatus(error.message || 'Error inesperat.',true));
