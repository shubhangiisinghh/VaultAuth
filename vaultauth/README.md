# VaultAuth

Multi-factor authentication for SPAs-built from scratch to understand what actually happens between a browser, a server session, and a TOTP second factor.

## Why I built this

I was adding 2FA to a React app backed by Spring Security. Every tutorial I found assumed one of two things: either your app is server-rendered (so a 302 redirect to a login page works fine), or you're going full stateless JWT (so there's no session to carry intermediate auth state between steps).

Neither worked for what I needed.

The problem shows up the moment you have a multi-step auth flow-username/password first, then a TOTP code- and your frontend is a React SPA. Step 1 succeeds. Now the server needs to tell the client "primary auth done, submit your TOTP code next." With a stateless JWT flow you have no server-side state to hold that intermediate step. With a classic session + redirect flow, your `fetch()` call gets back a 302 to a JSP page it can't render.

So I built VaultAuth to work through the problem properly: a stateful session that survives across auth steps, REST endpoints a SPA can actually call, and a `Location` header contract that tells the frontend exactly where to go next- without a single redirect the browser follows blindly.

---

## How it works

### The 401 + Location contract

Every unauthenticated `POST /api/**` call returns `401` with a `Location` header pointing to where the user needs to go:

```
HTTP/1.1 401 Unauthorized
Location: http://localhost:5173/login?step=totp
Content-Type: application/json

{"error": "Authentication required", "challengeUrl": "..."}
```

The React SPA reads this header and navigates. No HTML, no JSP, no 302 that the browser follows automatically. Just a machine-readable signal the client can act on.

### The multi-step session

The server uses `HttpSession` to carry partial auth state between steps:

```
Step 1 — POST /api/auth/login
  username + password OK → session["VA_AUTH_STEP"] = "TOTP_PENDING"
  → 200 { step: "TOTP_PENDING" }

Step 2 — POST /api/auth/totp
  6-digit code OK → session["VA_AUTHENTICATED"] = true
  → 200 { step: "COMPLETE" }
```

If the user tries to hit any protected endpoint between step 1 and step 2, the filter intercepts and sends them back to the TOTP step- not the start of login.

### Why not JWT?

JWT is stateless. That means you can't invalidate a token mid-flow, and you can't hold "step 1 done, step 2 pending" state server-side without a separate store anyway. For a multi-step auth flow with server-enforced step sequencing, a session is the right tool.

---

## Features

- **Primary auth** - username + password via `POST /api/auth/login`
- **TOTP second factor** - RFC 6238 compliant (30-second window, ±1 drift tolerance), compatible with Google Authenticator and Authy
- **Secret question fallback**  per-user choice of secondary factor
- **Force-password-change flow**  user is authenticated but restricted until password is updated
- **TOTP enrolment** - QR code generation, scan with any authenticator app, confirm with first code
- **Session audit log** - every login attempt, TOTP success/failure, logout, and lockout written to `va_session_events`
- **Account lockout** - after 5 consecutive failed attempts
- **Role-based access** - ADMIN, CLINICIAN, VIEWER roles with protected routes on both frontend and backend
- **Admin dashboard** - paginated audit log, user management, unlock accounts, force password resets

---

## Tech stack

| Layer | Tech |
|---|---|
| Backend API | Java 17, Spring Boot 3, Spring Security |
| TOTP | [java-totp](https://github.com/samdjstevens/java-totp) (RFC 6238) |
| Database | MySQL 8 (H2 for tests) |
| ORM | Spring Data JPA / Hibernate |
| Frontend | React 18, TypeScript, React Router v6 |
| Build | Maven (backend), Vite (frontend) |
| Infra | Docker, Docker Compose, GitHub Actions |

---

## Project structure

```
vaultauth/
├── vaultauth-backend/
│   ├── api/                          # Pure Java — no web deps
│   │   └── src/main/java/dev/vaultauth/
│   │       ├── auth/                 # AuthenticationScheme interface + exception
│   │       ├── credentials/          # Credentials, UsernamePasswordCredentials, TotpCredentials
│   │       ├── model/                # User, TotpSecret, SessionEvent, enums
│   │       └── service/              # AuthService, TotpService, SessionAuditService, repositories
│   └── omod/                         # Web layer
│       └── src/main/java/dev/vaultauth/
│           ├── web/filter/           # AuthenticationFilter — the 401+Location logic
│           ├── web/rest/             # AuthController, TotpSetupController, AdminController
│           └── web/config/           # SecurityConfig, CorsConfig, TotpConfig
└── vaultauth-frontend/
    └── src/
        ├── api/                      # authClient.ts (reads Location on 401), session.ts
        ├── auth/                     # AuthContext, ProtectedRoute, useAuthChallenge
        ├── components/               # PasswordForm, TotpInput, ChallengeForm, ForcePasswordChange
        └── pages/                    # LoginPage, DashboardPage, AdminPage
```

The `api/` + `omod/` split keeps pure business logic separate from the web layer - `api` has zero web dependencies, `omod` depends on `api`. This makes the core auth logic independently testable without spinning up a servlet container.

---

## Getting started

### Prerequisites

- Java 17+
- Node 20+
- MySQL 8 running locally (or use Docker Compose)
- Maven 3.9+

### Option 1 — Docker Compose (easiest)

```bash
git clone https://github.com/yourusername/vaultauth.git
cd vaultauth
docker compose up --build
```

Frontend: http://localhost:5173
Backend:  http://localhost:8080

### Option 2 — Run locally

**Start MySQL**
```bash
# if you have MySQL running locally, create the DB:
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS vaultauth;"
```

**Backend**
```bash
cd vaultauth-backend
mvn clean install -DskipTests
cd omod
mvn spring-boot:run
```

**Frontend** (in a new terminal)
```bash
cd vaultauth-frontend
npm install
npm run dev
```

Open http://localhost:5173

---

## Demo accounts

Three accounts are seeded on first start:

| Username | Password   | Role      | 2FA     | Notes                          |
|----------|------------|-----------|---------|--------------------------------|
| admin    | admin123   | ADMIN     | off     | Can access audit log at /admin |
| alice    | alice123   | CLINICIAN | off     | Enable 2FA from the dashboard  |
| viewer   | viewer123  | VIEWER    | off     | Force-password-change on login |

Log in as `viewer` to see the force-password-change flow in action.
Log in as `admin`, then go to `/admin` to see the session audit log.
Log in as `alice`, then enable TOTP from the dashboard to test the full 2FA flow.

---

## API reference

### Auth endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/login` | Step 1 — username + password |
| POST | `/api/auth/totp` | Step 2a — TOTP code |
| POST | `/api/auth/challenge` | Step 2b — secret question answer |
| POST | `/api/auth/password` | Force-change-password step |
| GET  | `/api/auth/session` | Current session state |
| POST | `/api/auth/logout` | Invalidate session |
| GET  | `/api/auth/totp/setup` | Get QR code for TOTP enrolment |
| POST | `/api/auth/totp/confirm` | Confirm TOTP enrolment |

### Admin endpoints (ADMIN role required)

| Method | Path | Description |
|--------|------|-------------|
| GET  | `/api/admin/events` | Paginated session audit log |
| GET  | `/api/admin/users` | All users |
| POST | `/api/admin/users/{id}/unlock` | Unlock account |
| POST | `/api/admin/users/{id}/force-password-change` | Force reset on next login |

### Response contract

**Successful step** — frontend navigates to next screen:
```json
{ "step": "TOTP_PENDING" }
```

**Auth challenge** — any protected endpoint when not authenticated:
```
HTTP 401
Location: http://localhost:5173/login?step=totp
```

**Error** — bad credentials, wrong code, locked account:
```json
{ "error": "Invalid or expired TOTP code." }
```

---

## Running tests

```bash
cd vaultauth-backend
mvn test
```

Tests use H2 in-memory database - no MySQL needed.

Key test cases:
- `AuthenticationFilterTest` - verifies the 401+Location behaviour for authenticated, unauthenticated, and mid-flow sessions
- `TotpServiceTest`- valid code passes, wrong code fails, no-confirmed-secret always fails

---

## Things I learned building this

**HttpSession is underrated for multi-step flows.** Everyone reaches for JWT these days, but for a flow that has server-enforced step sequencing ("you cannot submit TOTP before passing step 1"), holding state server-side is actually simpler - no need to encode step state into a token and verify it on every request.

**The 302 vs 401 distinction matters a lot.** A browser follows a 302 automatically. A `fetch()` call doesn't- it gets the response back with `type: "opaqueredirect"` and a 0 status, which you can't usefully inspect. Sending 401 + Location instead gives the SPA full control over how to navigate.

**TOTP window tolerance needs thought.** RFC 6238 specifies a 30-second time window. The `java-totp` library allows ±1 window by default, which means a code stays valid for up to 90 seconds in practice. That's a tradeoff- too tight and clock drift breaks legitimate users, too loose and the replay window grows. I left it at ±1 which is the standard recommendation.

**The `Location` header needs explicit CORS exposure.** By default, CORS only exposes a small set of response headers to JavaScript. To let `response.headers.get('Location')` work in the browser, the backend has to explicitly list `Location` in `Access-Control-Expose-Headers`. Took me an embarrassingly long time to debug that one.

---

## License

MIT
