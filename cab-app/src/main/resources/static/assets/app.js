'use strict';
/*
 * CabGo frontend: plain HTML + JavaScript, no build step, served by the Spring Boot app itself.
 *
 * HOW IT WORKS
 *  - Login/Register call /api/v1/auth/*, which returns a JWT. We keep it in sessionStorage and send it on
 *    every call as "Authorization: Bearer <token>".
 *  - The page then POLLS the server every 2 seconds (GET /trips/mine, GET /drivers/me, notifications) and
 *    redraws. Polling is the simplest approach; ALTERNATIVES are WebSocket / Server-Sent Events (push).
 *  - Rider: click the map to choose pickup, then drop -> see price -> request ride -> pay -> rate.
 *  - Driver: click the map to set your location, go online, accept the assigned trip, start, complete.
 *  - The map is Leaflet (free). Markers are drawn from the data the server returns.
 */

// ===================================================================== config & state
const API = '/api/v1';
// Map starts here. Change it to your city.
const DEFAULT_CENTER = [27.4924, 77.6737];
const POLL_MS = 2000;
const ACTIVE = ['DRIVER_ASSIGNED', 'IN_PROGRESS'];

const state = {
  token: sessionStorage.getItem('token'),
  role: sessionStorage.getItem('role'),
  profileId: Number(sessionStorage.getItem('profileId')) || null,
  profile: null,          // driver profile (drivers only)
  trips: [],              // my recent trips, newest first
  pickup: null,           // rider's chosen points {lat, lng}
  drop: null,
  pickMode: 'pickup',
  quote: null,
  payKeys: {},            // tripId -> Idempotency-Key (one per payment attempt)
  lastNotificationId: 0,
  notificationUrl: '',    // filled from /api/v1/config; empty = no notification service (e.g. cloud demo)
  pollHandle: null,
  simulating: false,
  fittedTripId: null,
};

let map = null;
let mapLayer = null;
let lastSidebarHtml = '';

// ===================================================================== small helpers
const $ = (id) => document.getElementById(id);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const money = (n) => (n == null ? '-' : '\u20B9' + Number(n).toFixed(2));

/** Escapes text before putting it in HTML, so a name like "<script>" can never run (XSS protection). */
function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function toast(message, kind = '') {
  const el = document.createElement('div');
  el.className = 'toast ' + kind;
  el.textContent = message;
  $('toasts').appendChild(el);
  setTimeout(() => el.remove(), 5000);
}

/** Straight-line distance in km (same Haversine formula as the backend). */
function distanceKm(lat1, lng1, lat2, lng2) {
  const rad = (d) => (d * Math.PI) / 180;
  const dLat = rad(lat2 - lat1);
  const dLng = rad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/** Rough arrival estimate: straight-line distance at 25 km/h. */
function etaMinutes(lat1, lng1, lat2, lng2) {
  return Math.max(1, Math.ceil((distanceKm(lat1, lng1, lat2, lng2) / 25) * 60));
}

// ===================================================================== API wrapper
/**
 * Calls the backend. Options: method, body (object), headers, token (override), auth:false (send no token).
 * Throws an Error whose message is the server's error message, so callers can just toast(err.message).
 */
async function api(path, options = {}) {
  const { method = 'GET', body, headers = {}, token, auth = true } = options;
  const init = { method, headers: { ...headers } };
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  const bearer = token || (auth ? state.token : null);
  if (bearer) init.headers['Authorization'] = 'Bearer ' + bearer;

  const response = await fetch(API + path, init);
  const text = await response.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch (e) { data = null; }
  }
  if (!response.ok) {
    if (response.status === 401 && auth && !token && state.token) {
      logout('Your session expired. Please log in again.');
    }
    const error = new Error((data && data.message) || 'Request failed (' + response.status + ')');
    error.status = response.status;
    throw error;
  }
  return data;
}

// ===================================================================== session
function saveSession(auth) {
  state.token = auth.token;
  state.role = auth.role;
  state.profileId = auth.profileId;
  sessionStorage.setItem('token', auth.token);
  sessionStorage.setItem('role', auth.role);
  sessionStorage.setItem('profileId', String(auth.profileId));
}

function logout(message) {
  stopPolling();
  sessionStorage.clear();
  Object.assign(state, {
    token: null, role: null, profileId: null, profile: null, trips: [],
    pickup: null, drop: null, pickMode: 'pickup', quote: null, lastNotificationId: 0, fittedTripId: null,
  });
  if (map) { map.remove(); map = null; mapLayer = null; }
  lastSidebarHtml = '';
  $('app-view').hidden = true;
  $('user-badge').hidden = true;
  $('auth-view').hidden = false;
  $('auth-error').textContent = message || '';
  renderDemoDrivers();
}

// ===================================================================== login / register screen
function showAuthTab(which) {
  const login = which === 'login';
  $('login-form').hidden = !login;
  $('register-form').hidden = login;
  $('tab-login').classList.toggle('active', login);
  $('tab-register').classList.toggle('active', !login);
  $('auth-error').textContent = '';
}

async function handleLogin(event) {
  event.preventDefault();
  $('auth-error').textContent = '';
  try {
    const auth = await api('/auth/login', {
      method: 'POST', auth: false,
      body: { phone: $('login-phone').value.trim(), password: $('login-password').value },
    });
    saveSession(auth);
    await enterApp();
  } catch (e) {
    $('auth-error').textContent = e.message;
  }
}

async function handleRegister(event) {
  event.preventDefault();
  $('auth-error').textContent = '';
  const role = $('reg-role').value;
  try {
    const auth = await api('/auth/register', {
      method: 'POST', auth: false,
      body: {
        name: $('reg-name').value.trim(),
        phone: $('reg-phone').value.trim(),
        password: $('reg-password').value,
        role,
        vehicleNumber: role === 'DRIVER' ? $('reg-vehicle').value.trim() : null,
      },
    });
    saveSession(auth);
    await enterApp();
  } catch (e) {
    $('auth-error').textContent = e.message;
  }
}

// ---- demo drivers (developer tool) ----
function loadDemoDrivers() {
  try { return JSON.parse(localStorage.getItem('demoDrivers') || '[]'); } catch (e) { return []; }
}

function renderDemoDrivers() {
  const list = $('demo-drivers');
  list.innerHTML = loadDemoDrivers().map((d) =>
    `<li><span>${esc(d.name)} &middot; ${esc(d.phone)}</span>
       <button class="btn small" type="button" data-fill="${esc(d.phone)}">Use</button></li>`).join('');
}

async function seedDemoDrivers() {
  const button = $('btn-seed');
  button.disabled = true;
  $('auth-error').textContent = '';
  try {
    const demo = loadDemoDrivers();
    for (let i = 0; i < 5; i++) {
      const phone = '9' + String(Math.floor(Math.random() * 1e9)).padStart(9, '0');
      const name = 'Demo Driver ' + (demo.length + 1);
      const auth = await api('/auth/register', {
        method: 'POST', auth: false,
        body: { name, phone, password: 'password123', role: 'DRIVER', vehicleNumber: 'DEMO' + (1000 + demo.length) },
      });
      // Spread the drivers within roughly 1 km of the map centre, then put each one online.
      const latitude = DEFAULT_CENTER[0] + (Math.random() - 0.5) * 0.02;
      const longitude = DEFAULT_CENTER[1] + (Math.random() - 0.5) * 0.02;
      await api('/drivers/me/location', { method: 'PUT', token: auth.token, body: { latitude, longitude } });
      await api('/drivers/me/status', { method: 'PUT', token: auth.token, body: { status: 'AVAILABLE' } });
      demo.push({ name, phone });
    }
    localStorage.setItem('demoDrivers', JSON.stringify(demo));
    renderDemoDrivers();
    toast('5 demo drivers are online near the map centre.');
  } catch (e) {
    $('auth-error').textContent = e.message;
  } finally {
    button.disabled = false;
  }
}

// ===================================================================== entering the app
async function enterApp() {
  $('auth-view').hidden = true;
  $('app-view').hidden = false;
  $('user-badge').hidden = false;
  $('user-text').textContent = (state.role === 'RIDER' ? 'Rider' : 'Driver') + ' #' + state.profileId;
  initMap();
  await poll();
  startPolling();
}

function initMap() {
  if (map) return;
  map = L.map('map').setView(DEFAULT_CENTER, 14);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors',
  }).addTo(map);
  mapLayer = L.layerGroup().addTo(map);
  map.on('click', onMapClick);
}

function startPolling() {
  stopPolling();
  state.pollHandle = setInterval(poll, POLL_MS);
}

function stopPolling() {
  if (state.pollHandle) clearInterval(state.pollHandle);
  state.pollHandle = null;
}

/** One refresh cycle: fetch my trips (and my driver profile), then redraw. */
async function poll() {
  if (!state.token) return;
  try {
    state.trips = await api('/trips/mine');
    if (state.role === 'DRIVER') state.profile = await api('/drivers/me');
    render();
  } catch (e) {
    if (e.status !== 401) console.warn('poll failed:', e.message);
  }
  pollNotifications();
}

async function pollNotifications() {
  if (!state.notificationUrl) return;
  try {
    const url = state.notificationUrl + '/api/notifications?role=' + state.role + '&id=' + state.profileId +
      '&afterId=' + state.lastNotificationId;
    const response = await fetch(url);
    if (!response.ok) return;
    const items = await response.json();
    for (const n of items) {
      state.lastNotificationId = Math.max(state.lastNotificationId, n.id);
      toast(n.message, 'notify');
    }
  } catch (e) {
    // notification-service not running: ignore silently
  }
}

// ===================================================================== map interaction
async function onMapClick(event) {
  const point = event.latlng.wrap();
  if (state.role === 'RIDER') {
    if (currentRiderTrip()) return;               // can't pick points while a ride is active
    if (state.pickMode === 'pickup') {
      state.pickup = { lat: point.lat, lng: point.lng };
      state.pickMode = 'drop';
    } else {
      state.drop = { lat: point.lat, lng: point.lng };
    }
    render();
    await refreshQuote();
  } else if (state.role === 'DRIVER') {
    await setDriverLocation(point.lat, point.lng);
  }
}

async function refreshQuote() {
  if (!state.pickup || !state.drop) return;
  try {
    state.quote = await api('/trips/quote', {
      method: 'POST',
      body: { pickupLat: state.pickup.lat, pickupLng: state.pickup.lng, dropLat: state.drop.lat, dropLng: state.drop.lng },
    });
  } catch (e) {
    state.quote = null;
    toast(e.message, 'error');
  }
  render();
}

async function setDriverLocation(lat, lng) {
  try {
    state.profile = await api('/drivers/me/location', { method: 'PUT', body: { latitude: lat, longitude: lng } });
    render();
  } catch (e) {
    toast(e.message, 'error');
  }
}

// ===================================================================== rendering
function emojiIcon(emoji) {
  return L.divIcon({ html: emoji, className: 'emoji-marker', iconSize: [30, 30], iconAnchor: [15, 15] });
}

function render() {
  renderSidebar();
  renderMap();
}

/** The trip the rider should currently see: an active ride, or a finished one still waiting for payment/rating. */
function currentRiderTrip() {
  const active = state.trips.find((t) => ACTIVE.includes(t.status));
  if (active) return active;
  return state.trips.find((t) => t.status === 'COMPLETED' && (t.paymentStatus !== 'CAPTURED' || t.ratingScore == null)) || null;
}

function currentDriverTrip() {
  return state.trips.find((t) => ACTIVE.includes(t.status)) || null;
}

function renderMap() {
  if (!map) return;
  mapLayer.clearLayers();
  const trip = state.role === 'RIDER' ? currentRiderTrip() : currentDriverTrip();
  const bounds = [];

  if (state.role === 'RIDER' && !trip) {
    if (state.pickup) {
      L.marker([state.pickup.lat, state.pickup.lng], { icon: emojiIcon('\uD83D\uDFE2') }).addTo(mapLayer);
      bounds.push([state.pickup.lat, state.pickup.lng]);
    }
    if (state.drop) {
      L.marker([state.drop.lat, state.drop.lng], { icon: emojiIcon('\uD83D\uDD34') }).addTo(mapLayer);
      bounds.push([state.drop.lat, state.drop.lng]);
    }
    if (state.pickup && state.drop) {
      L.polyline([[state.pickup.lat, state.pickup.lng], [state.drop.lat, state.drop.lng]],
        { color: '#1a73e8', dashArray: '6 8' }).addTo(mapLayer);
    }
  }

  if (trip) {
    const pickup = [trip.pickupLat, trip.pickupLng];
    const drop = [trip.dropLat, trip.dropLng];
    L.marker(pickup, { icon: emojiIcon('\uD83D\uDFE2') }).addTo(mapLayer);
    L.marker(drop, { icon: emojiIcon('\uD83D\uDD34') }).addTo(mapLayer);
    L.polyline([pickup, drop], { color: '#1a73e8', dashArray: '6 8' }).addTo(mapLayer);
    bounds.push(pickup, drop);
    if (state.role === 'RIDER' && trip.driverLat != null && ACTIVE.includes(trip.status)) {
      L.marker([trip.driverLat, trip.driverLng], { icon: emojiIcon('\uD83D\uDE95') }).addTo(mapLayer);
      bounds.push([trip.driverLat, trip.driverLng]);
    }
  }

  if (state.role === 'DRIVER' && state.profile && state.profile.latitude != null) {
    const me = [state.profile.latitude, state.profile.longitude];
    L.marker(me, { icon: emojiIcon('\uD83D\uDE95') }).addTo(mapLayer);
    bounds.push(me);
  }

  // Zoom to fit the trip once when a new trip appears (not on every refresh, which would fight the user).
  if (trip && state.fittedTripId !== trip.id && bounds.length) {
    map.fitBounds(bounds, { padding: [50, 50], maxZoom: 16 });
    state.fittedTripId = trip.id;
  }
}

function renderSidebar() {
  const html = state.role === 'RIDER' ? riderHtml() : driverHtml();
  if (html !== lastSidebarHtml) {          // only touch the DOM when something changed (avoids flicker)
    $('sidebar').innerHTML = html;
    lastSidebarHtml = html;
  }
}

// ---------------------------------------------------------------- rider UI
function riderHtml() {
  const trip = currentRiderTrip();
  let html = '';

  if (!trip) {
    const canRequest = state.pickup && state.drop;
    html += `<div class="card"><h3>Book a ride</h3>
      <p class="hint">Click the map to set your <b>${state.pickMode === 'pickup' ? 'pickup \uD83D\uDFE2' : 'drop \uD83D\uDD34'}</b>.</p>
      <div class="row">
        <button class="btn small ${state.pickMode === 'pickup' ? 'primary' : ''}" data-action="mode-pickup">Set pickup</button>
        <button class="btn small ${state.pickMode === 'drop' ? 'primary' : ''}" data-action="mode-drop">Set drop</button>
        <button class="btn small ghost" data-action="clear-points">Clear</button>
      </div>`;
    if (state.quote) {
      const surged = Number(state.quote.surgeMultiplier) > 1;
      html += `<div class="kv"><span>Distance</span><span>${esc(state.quote.distanceKm)} km</span></div>
        <div class="kv"><span>Base fare</span><span>${money(state.quote.baseFare)}</span></div>
        <div class="kv"><span>Demand multiplier</span><span class="${surged ? 'surge' : ''}">x${esc(state.quote.surgeMultiplier)}${surged ? ' (high demand)' : ''}</span></div>
        <div class="kv"><span><b>Total</b></span><span class="big">${money(state.quote.total)}</span></div>`;
    }
    html += `<button class="btn primary" style="width:100%;margin-top:10px" data-action="request" ${canRequest ? '' : 'disabled'}>Request ride</button></div>`;
  } else {
    html += riderTripCard(trip);
  }
  return html + historyHtml(trip);
}

function riderTripCard(t) {
  let html = `<div class="card"><h3>Trip #${t.id} <span class="badge ${esc(t.status)}">${esc(t.status.replace(/_/g, ' '))}</span></h3>`;
  if (t.driverName) {
    const rating = t.driverRating > 0 ? t.driverRating.toFixed(1) + ' \u2605' : 'new';
    html += `<div class="kv"><span>Driver</span><span>${esc(t.driverName)} (${esc(rating)})</span></div>
      <div class="kv"><span>Vehicle</span><span>${esc(t.driverVehicle)}</span></div>`;
  }
  if (t.status === 'DRIVER_ASSIGNED' && t.driverLat != null) {
    html += `<div class="kv"><span>Driver arrives in</span><span>~${etaMinutes(t.driverLat, t.driverLng, t.pickupLat, t.pickupLng)} min</span></div>`;
  }
  html += `<div class="kv"><span>Fare</span><span class="big">${money(t.fare)}</span></div>`;
  if (Number(t.surgeMultiplier) > 1) {
    html += `<div class="kv"><span>Demand multiplier</span><span class="surge">x${esc(t.surgeMultiplier)}</span></div>`;
  }

  if (t.status === 'DRIVER_ASSIGNED') {
    html += `<button class="btn danger" style="width:100%;margin-top:8px" data-action="cancel" data-id="${t.id}">Cancel ride</button>`;
  }
  if (t.status === 'COMPLETED') {
    if (t.paymentStatus === 'PENDING') {
      html += `<button class="btn success" style="width:100%;margin-top:8px" data-action="pay" data-id="${t.id}">Pay ${money(t.fare)}</button>`;
    } else if (t.ratingScore == null) {
      html += `<p class="hint">Paid. How was your driver?</p><div class="stars">` +
        [1, 2, 3, 4, 5].map((n) => `<button data-action="rate" data-id="${t.id}" data-score="${n}">\u2605</button>`).join('') +
        `</div>`;
    }
  }
  return html + '</div>';
}

function historyHtml(current) {
  const past = state.trips.filter((t) => !current || t.id !== current.id).slice(0, 6);
  if (!past.length) return '';
  return `<div class="card"><h3>Recent trips</h3><div class="history">` +
    past.map((t) => `<div><span>#${t.id} &middot; ${esc(t.status.replace(/_/g, ' '))}</span><span>${money(t.fare)}</span></div>`).join('') +
    `</div></div>`;
}

// ---------------------------------------------------------------- driver UI
function driverHtml() {
  const p = state.profile;
  if (!p) return '<div class="card">Loading...</div>';
  const trip = currentDriverTrip();
  const hasLocation = p.latitude != null;
  const rating = p.ratingCount > 0 ? p.ratingAverage.toFixed(1) + ' \u2605 (' + p.ratingCount + ')' : 'no ratings yet';

  let html = `<div class="card"><h3>${esc(p.name)} <span class="badge ${esc(p.status)}">${esc(p.status.replace(/_/g, ' '))}</span></h3>
    <div class="kv"><span>Vehicle</span><span>${esc(p.vehicleNumber)}</span></div>
    <div class="kv"><span>Rating</span><span>${esc(rating)}</span></div>
    <div class="kv"><span>Location</span><span>${hasLocation ? p.latitude.toFixed(4) + ', ' + p.longitude.toFixed(4) : 'not set'}</span></div>
    <p class="hint">Click the map to move yourself. Riders can only match you while you are ONLINE with a location.</p>
    <div class="row">
      <button class="btn success grow" data-action="go-online" ${(p.status === 'AVAILABLE' || p.status === 'ON_TRIP' || !hasLocation) ? 'disabled' : ''}>Go online</button>
      <button class="btn grow" data-action="go-offline" ${(p.status === 'OFFLINE' || p.status === 'ON_TRIP') ? 'disabled' : ''}>Go offline</button>
    </div></div>`;

  if (trip) {
    html += `<div class="card"><h3>Trip #${trip.id} <span class="badge ${esc(trip.status)}">${esc(trip.status.replace(/_/g, ' '))}</span></h3>
      <div class="kv"><span>Rider</span><span>${esc(trip.riderName)}</span></div>
      <div class="kv"><span>Fare</span><span class="big">${money(trip.fare)}</span></div>`;
    if (trip.status === 'DRIVER_ASSIGNED') {
      html += `<div class="row">
        <button class="btn grow" data-action="drive-pickup" ${state.simulating ? 'disabled' : ''}>Simulate: drive to pickup</button>
        <button class="btn primary grow" data-action="start" data-id="${trip.id}">Start trip</button>
        <button class="btn danger grow" data-action="cancel" data-id="${trip.id}">Cancel</button></div>`;
    } else if (trip.status === 'IN_PROGRESS') {
      html += `<div class="row">
        <button class="btn grow" data-action="drive-drop" ${state.simulating ? 'disabled' : ''}>Simulate: drive to drop</button>
        <button class="btn success grow" data-action="complete" data-id="${trip.id}">Complete trip</button></div>`;
    }
    html += '</div>';
  } else if (p.status === 'AVAILABLE') {
    html += `<div class="card"><p class="hint">Waiting for a ride request near you...</p></div>`;
  }
  return html + historyHtml(trip);
}

// ===================================================================== button actions (event delegation)
async function handleAction(action, dataset) {
  try {
    switch (action) {
      case 'mode-pickup': state.pickMode = 'pickup'; render(); break;
      case 'mode-drop': state.pickMode = 'drop'; render(); break;
      case 'clear-points':
        state.pickup = null; state.drop = null; state.quote = null; state.pickMode = 'pickup'; render(); break;

      case 'request': {
        const trip = await api('/trips', {
          method: 'POST',
          body: { pickupLat: state.pickup.lat, pickupLng: state.pickup.lng, dropLat: state.drop.lat, dropLng: state.drop.lng },
        });
        if (trip.status === 'NO_DRIVER_FOUND') {
          toast('No drivers available nearby. Seed demo drivers or try again.', 'error');
        } else {
          state.pickup = null; state.drop = null; state.quote = null; state.pickMode = 'pickup';
        }
        await poll();
        break;
      }
      case 'cancel': await api('/trips/' + dataset.id + '/cancel', { method: 'POST' }); await poll(); break;
      case 'start': await api('/trips/' + dataset.id + '/start', { method: 'POST' }); await poll(); break;
      case 'complete': await api('/trips/' + dataset.id + '/complete', { method: 'POST' }); await poll(); break;

      case 'pay': {
        // One Idempotency-Key per payment attempt: if the click is repeated, the server charges only once.
        const key = state.payKeys[dataset.id] || (state.payKeys[dataset.id] = crypto.randomUUID());
        await api('/trips/' + dataset.id + '/payment', { method: 'POST', headers: { 'Idempotency-Key': key } });
        toast('Payment successful.');
        await poll();
        break;
      }
      case 'rate':
        await api('/trips/' + dataset.id + '/rating', { method: 'POST', body: { score: Number(dataset.score) } });
        toast('Thanks for rating!');
        await poll();
        break;

      case 'go-online': await api('/drivers/me/status', { method: 'PUT', body: { status: 'AVAILABLE' } }); await poll(); break;
      case 'go-offline': await api('/drivers/me/status', { method: 'PUT', body: { status: 'OFFLINE' } }); await poll(); break;

      case 'drive-pickup': {
        const trip = currentDriverTrip();
        if (trip) await simulateDrive(trip.pickupLat, trip.pickupLng);
        break;
      }
      case 'drive-drop': {
        const trip = currentDriverTrip();
        if (trip) await simulateDrive(trip.dropLat, trip.dropLng);
        break;
      }
      default: break;
    }
  } catch (e) {
    toast(e.message, 'error');
    await poll();
  }
}

/** Moves the driver in 10 small steps (one location update per step), like a real driver app would. */
async function simulateDrive(toLat, toLng) {
  if (state.simulating || !state.profile || state.profile.latitude == null) return;
  state.simulating = true;
  render();
  try {
    const fromLat = state.profile.latitude;
    const fromLng = state.profile.longitude;
    const steps = 10;
    for (let i = 1; i <= steps; i++) {
      const lat = fromLat + ((toLat - fromLat) * i) / steps;
      const lng = fromLng + ((toLng - fromLng) * i) / steps;
      state.profile = await api('/drivers/me/location', { method: 'PUT', body: { latitude: lat, longitude: lng } });
      render();
      await sleep(700);
    }
  } finally {
    state.simulating = false;
    render();
  }
}

/** Asks the backend where the notification service is (the answer differs between local and cloud). */
async function loadConfig() {
  try {
    const response = await fetch(API + '/config');
    if (response.ok) state.notificationUrl = (await response.json()).notificationUrl || '';
  } catch (e) {
    state.notificationUrl = '';
  }
}

// ===================================================================== wire up the page
document.addEventListener('DOMContentLoaded', async () => {
  $('tab-login').addEventListener('click', () => showAuthTab('login'));
  $('tab-register').addEventListener('click', () => showAuthTab('register'));
  $('login-form').addEventListener('submit', handleLogin);
  $('register-form').addEventListener('submit', handleRegister);
  $('reg-role').addEventListener('change', () => { $('reg-vehicle-wrap').hidden = $('reg-role').value !== 'DRIVER'; });
  $('btn-logout').addEventListener('click', () => logout());
  $('btn-seed').addEventListener('click', seedDemoDrivers);

  // "Use" buttons next to demo drivers fill the login form
  $('demo-drivers').addEventListener('click', (e) => {
    const phone = e.target.dataset && e.target.dataset.fill;
    if (!phone) return;
    showAuthTab('login');
    $('login-phone').value = phone;
    $('login-password').value = 'password123';
  });

  // One click listener for every sidebar button (buttons are re-created on each redraw)
  $('sidebar').addEventListener('click', (e) => {
    const button = e.target.closest('[data-action]');
    if (button && !button.disabled) handleAction(button.dataset.action, button.dataset);
  });

  renderDemoDrivers();
  await loadConfig();
  if (state.token) enterApp(); // page refresh while logged in: go straight back into the app
});
