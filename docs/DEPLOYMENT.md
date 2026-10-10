# Free cloud deployment (Render + Neon, no credit card)

| Piece | Where | Cost |
|---|---|---|
| Code + CI | GitHub (+ GitHub Actions) | free |
| PostgreSQL | Neon (free tier) | free |
| Redis | Render Key Value (free, 25 MB, in-memory only) | free |
| cab-app (backend + web UI) | Render Web Service, Docker, free instance | free |
| Kafka + notification-service | **not deployed in the cloud demo** (`CAB_EVENTS_ENABLED=false`); run locally with `docker compose` | n/a |

Free-tier behaviour to expect: the app **sleeps after 15 minutes without traffic** and takes a few minutes to wake
(tiny 0.1 CPU); Redis data is lost on restart (the app rebuilds its index from PostgreSQL at startup).

## Environment variables for the Render web service

| Name | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<neon-host>/<database>?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Neon user |
| `SPRING_DATASOURCE_PASSWORD` | Neon password |
| `SPRING_DATA_REDIS_URL` | the **Internal** URL of the Render Key Value instance (`redis://red-...:6379`) |
| `SPRING_DATA_REDIS_TIMEOUT` | `2s` |
| `SPRING_DATA_REDIS_CONNECT_TIMEOUT` | `3s` |
| `JWT_SECRET` | a random string of 32+ characters (`openssl rand -base64 32`) |
| `CAB_EVENTS_ENABLED` | `false` |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `5` |

Render settings: Language **Docker**, Dockerfile path `cab-app/Dockerfile`, Docker build context directory `.`,
instance type **Free**, health check path `/actuator/health/liveness`.

## Troubleshooting
| Symptom | Fix |
|---|---|
| Build fails | Open the CI run on GitHub for the real compiler/test error |
| `Connection ... refused` to Redis | Key Value and web service must be in the **same region**; use the *Internal* URL |
| `password authentication failed` | Re-copy the Neon credentials (direct connection, pooling off) |
| Service restarts, log shows `OutOfMemoryError` | Add env `JAVA_TOOL_OPTIONS=-Xmx250m` |
| First page load is very slow | The service was asleep; wait ~2-4 minutes and refresh |
