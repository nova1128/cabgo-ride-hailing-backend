// k6 load test (k6 is free and open source).
//
// WHAT IT DOES
//   setup():  registers DRIVERS drivers (put online around the map centre) and RIDERS riders.
//   scenario "driver_pings":  every driver VU sends live-location updates in a loop.
//   scenario "ride_requests": every rider VU requests a ride; if a driver is assigned, the driver starts and
//                             completes it (which frees the driver), then the rider asks again.
//
// HOW TO USE IT FOR THE README "SQL vs Redis" NUMBERS
//   1. Start the app with CAB_MATCHING_STRATEGY=sql   and run this script -> note the numbers
//   2. Start the app with CAB_MATCHING_STRATEGY=redis and run this script -> note the numbers
//   Compare http_req_duration for trip_create, and look at cab_matching_duration_seconds in Grafana.
//
// RUN (Git Bash / Linux / macOS):
//   docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run - < loadtest/matching.js
// RUN (Windows PowerShell):
//   Get-Content loadtest/matching.js | docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run -
// Optional knobs: -e DRIVERS=100 -e RIDERS=30 -e DURATION=60s

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const DRIVERS = Number(__ENV.DRIVERS || 50);
const RIDERS = Number(__ENV.RIDERS || 20);
const DURATION = __ENV.DURATION || '30s';
const CENTER = { lat: 27.4924, lng: 77.6737 };
const JSON_HEADERS = { 'Content-Type': 'application/json' };

const tripsAssigned = new Counter('trips_assigned');
const tripsNoDriver = new Counter('trips_no_driver');

export const options = {
  scenarios: {
    driver_pings: {
      executor: 'constant-vus', vus: Math.min(DRIVERS, 30), duration: DURATION, exec: 'pingLocation',
    },
    ride_requests: {
      executor: 'constant-vus', vus: RIDERS, duration: DURATION, exec: 'requestRide', startTime: '2s',
    },
  },
  thresholds: {
    // The test FAILS if more than 5% of requests fail or the 95th percentile of ride requests exceeds 800 ms.
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:trip_create}': ['p(95)<800'],
  },
};

function auth(token) {
  return { headers: { ...JSON_HEADERS, Authorization: 'Bearer ' + token } };
}

function randomPhone() {
  return String(Math.floor(1e9 + Math.random() * 9e9));
}

function register(name, role, vehicle) {
  const response = http.post(`${BASE}/api/v1/auth/register`, JSON.stringify({
    name, phone: randomPhone(), password: 'password123', role, vehicleNumber: vehicle,
  }), { headers: JSON_HEADERS, tags: { name: 'register' } });
  if (response.status !== 201) {
    throw new Error('register failed: ' + response.status + ' ' + response.body);
  }
  return response.json();
}

function jitter(range) {
  return (Math.random() - 0.5) * range;
}

export function setup() {
  const drivers = [];
  for (let i = 0; i < DRIVERS; i++) {
    const auth0 = register('Load Driver ' + i, 'DRIVER', 'LOAD' + i);
    http.put(`${BASE}/api/v1/drivers/me/location`,
      JSON.stringify({ latitude: CENTER.lat + jitter(0.03), longitude: CENTER.lng + jitter(0.03) }), auth(auth0.token));
    http.put(`${BASE}/api/v1/drivers/me/status`, JSON.stringify({ status: 'AVAILABLE' }), auth(auth0.token));
    drivers.push({ id: auth0.profileId, token: auth0.token });
  }
  const riders = [];
  for (let i = 0; i < RIDERS; i++) {
    riders.push({ token: register('Load Rider ' + i, 'RIDER', null).token });
  }
  return { drivers, riders };
}

// Scenario 1: drivers send location updates continuously.
export function pingLocation(data) {
  const driver = data.drivers[(__VU - 1) % data.drivers.length];
  const response = http.put(`${BASE}/api/v1/drivers/me/location`,
    JSON.stringify({ latitude: CENTER.lat + jitter(0.03), longitude: CENTER.lng + jitter(0.03) }),
    { ...auth(driver.token), tags: { name: 'location_ping' } });
  check(response, { 'ping ok or conflict': (r) => r.status === 200 || r.status === 409 });
  sleep(0.5);
}

// Scenario 2: riders request rides; drivers complete them.
export function requestRide(data) {
  const rider = data.riders[(__VU - 1) % data.riders.length];
  const lat = CENTER.lat + jitter(0.02);
  const lng = CENTER.lng + jitter(0.02);
  const response = http.post(`${BASE}/api/v1/trips`, JSON.stringify({
    pickupLat: lat, pickupLng: lng, dropLat: lat + 0.01, dropLng: lng + 0.01,
  }), { ...auth(rider.token), tags: { name: 'trip_create' } });

  if (!check(response, { 'trip created': (r) => r.status === 201 })) {
    sleep(2.5);
    return;
  }
  const trip = response.json();
  if (trip.status === 'DRIVER_ASSIGNED') {
    tripsAssigned.add(1);
    const driver = data.drivers.find((d) => d.id === trip.driverId);
    if (driver) {
      http.post(`${BASE}/api/v1/trips/${trip.id}/start`, null, auth(driver.token));
      http.post(`${BASE}/api/v1/trips/${trip.id}/complete`, null, auth(driver.token));
    }
  } else {
    tripsNoDriver.add(1);
  }
  sleep(2.5); // keeps each rider under the 30 requests/minute rate limit
}
