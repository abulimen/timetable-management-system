# Production Environment Template

Use this as a baseline for production/staging deployment secrets.

## Backend (`SPRING_PROFILES_ACTIVE=prod`)

```bash
SPRING_PROFILES_ACTIVE=prod

DB_URL=jdbc:mysql://<host>:3306/timetable_db?useSSL=true&serverTimezone=UTC
DB_USERNAME=<db_user>
DB_PASSWORD=<db_password>

JWT_SECRET=<long-random-secret>
JWT_ACCESS_TOKEN_EXPIRATION=3600000
JWT_REFRESH_TOKEN_EXPIRATION=604800000

BREVO_API_KEY=<brevo_api_key>
BREVO_SENDER_EMAIL=noreply@example.com
BREVO_SENDER_NAME=University Timetable

APP_LOGIN_URL=https://app.example.com/login
APP_CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
```

## Frontend Runtime Config

Edit `frontend/src/assets/runtime-config.js` during deployment:

```js
window.__BUTMS_CONFIG__ = window.__BUTMS_CONFIG__ || {};
window.__BUTMS_CONFIG__.apiBaseUrl = 'https://api.example.com';
```
