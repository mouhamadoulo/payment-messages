// Tir de charge sur l'API REST.
//
//   k6 run infra/load/k6-api.js
//   k6 run -e BASE_URL=http://localhost:8080 -e VUS=100 -e DURATION=5m infra/load/k6-api.js
//
// Mesure ce que le plan d'amélioration prétend avoir corrigé : le p95 des listes
// (pagination par offset -> curseur, projections sans payload) et le coût de /stats
// (agrégat mis en cache + ETag). Les seuils ci-dessous font échouer le tir : un tir qui
// ne peut pas échouer ne mesure rien.

import http from 'k6/http';
import { check, group } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || 'admin';
const API = `${BASE_URL}/api/v1`;

// Latences suivies séparément : agrégées, la lecture d'un message masquerait le coût
// d'une page de liste.
const listDuration = new Trend('payment_list_duration', true);
const cursorDuration = new Trend('payment_cursor_duration', true);
const statsDuration = new Trend('payment_stats_duration', true);

export const options = {
  scenarios: {
    lecture: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '30s', target: Number(__ENV.VUS || 50) },
        { duration: __ENV.DURATION || '2m', target: Number(__ENV.VUS || 50) },
        { duration: '15s', target: 0 },
      ],
    },
  },
  thresholds: {
    // Le curseur doit rester insensible à la profondeur : seuil plus serré que l'offset.
    payment_list_duration: ['p(95)<500'],
    payment_cursor_duration: ['p(95)<300'],
    // Servi depuis le cache applicatif : au-delà, le cache ne joue plus son rôle.
    payment_stats_duration: ['p(95)<150'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const response = http.post(
    `${API}/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  check(response, { 'authentification acceptée': (r) => r.status === 200 });

  if (response.status !== 200) {
    throw new Error(`Authentification refusée (${response.status}) : ${response.body}`);
  }

  return { token: response.json('token') };
}

export default function (data) {
  const params = {
    headers: {
      Authorization: `Bearer ${data.token}`,
      Accept: 'application/json',
    },
  };

  group('liste paginée', () => {
    // Page tirée au hasard dans les 20 premières : rester sur la page 0 mesurerait le
    // cache de PostgreSQL, pas le coût de l'OFFSET.
    const page = Math.floor(Math.random() * 20);
    const response = http.get(`${API}/messages?page=${page}&size=20&sort=receivedAt,desc`, params);

    listDuration.add(response.timings.duration);
    check(response, {
      'liste 200': (r) => r.status === 200,
      'liste sans payload': (r) => r.status !== 200 || !String(r.body).includes('"payload"'),
    });
  });

  group('pagination par curseur', () => {
    let cursor = null;
    // Trois pages consécutives : le coût doit rester plat, c'est tout l'intérêt du keyset.
    for (let i = 0; i < 3; i++) {
      const url = cursor
        ? `${API}/messages/cursor?size=20&cursor=${encodeURIComponent(cursor)}`
        : `${API}/messages/cursor?size=20`;
      const response = http.get(url, params);

      cursorDuration.add(response.timings.duration);
      check(response, { 'curseur 200': (r) => r.status === 200 });

      if (response.status !== 200) {
        break;
      }
      cursor = response.json('nextCursor');
      if (!cursor) {
        break;
      }
    }
  });

  group('statistiques', () => {
    const response = http.get(`${API}/messages/stats`, params);

    statsDuration.add(response.timings.duration);
    check(response, {
      'stats 200': (r) => r.status === 200,
      'stats avec ETag': (r) => r.headers['Etag'] !== undefined,
    });
  });
}
