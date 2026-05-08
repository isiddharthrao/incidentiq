# IncidentIQ — AI-Based Incident Management System

A college-project IT/DevOps incident management system inspired by PagerDuty/Jira-Service-Desk. Engineers report and resolve incidents like "API gateway returning 502" or "Deployment to staging failed". Auto-categorization, priority suggestion, similar-incident retrieval, resolution suggestions, and thread summarization are powered by Google Gemini via Spring AI.

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3.4 |
| Persistence | Spring Data JPA + Hibernate, MySQL 8/9 |
| Auth | Spring Security (form login, BCrypt, session-based) |
| View | Thymeleaf, custom CSS (no framework) |
| AI | Spring AI 1.0 + Google AI Studio (`gemini-2.0-flash`) |
| PDF | iText 7 Community |
| Build | Maven |
| Boilerplate | Lombok |

---

## Prerequisites

- JDK 17 (Temurin, OpenJDK, or any 17 build)
- Maven 3.9+
- MySQL 8 or 9 running locally on `:3306`
- A Google AI Studio API key (free tier — grab one at https://aistudio.google.com/app/apikey)

On macOS:

```bash
brew install openjdk@17
brew install maven
brew install mysql && brew services start mysql
```

`openjdk@17` is keg-only on Homebrew. Add to your shell:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## Setup

```bash
git clone <repo-url> incident-iq
cd incident-iq
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Edit `src/main/resources/application.properties` and fill in:
- `spring.datasource.password` — your MySQL root password (empty if Homebrew-default)
- `app.gemini.api-key` — your AI Studio key (must start with `AIza...`)

Schema is auto-created (the JDBC URL has `createDatabaseIfNotExist=true` and `ddl-auto=update`). The default admin account is seeded on first boot:

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |

**Change the admin password before any real demo.**

---

## Run

```bash
mvn spring-boot:run
```

Open http://localhost:8080/ — redirects to login. Sign in with `admin` / `admin123`.

---

## Project structure

```
src/main/java/com/incidentmgmt/
├── IncidentIqApplication.java
├── config/        SecurityConfig, AdminSeeder, CustomUserDetails(Service)
├── controller/    Auth, Dashboard, User, Incident
├── dto/           form-binding objects (Incident*Dto, UserDto, CommentDto)
├── entity/        JPA entities + enums (Role, Priority, Category, IncidentStatus)
├── exception/     custom exceptions (DuplicateUsernameException, …)
├── repository/    Spring Data JPA repos
└── service/       business logic (UserService, IncidentService)
                   AiService, GeminiClient, PromptTemplates  ← phase 6
                   PdfService                                ← phase 9

src/main/resources/
├── application.properties       (gitignored — local secrets)
├── application.properties.example
├── schema.sql                   (FULLTEXT index)
├── static/css/main.css
└── templates/
    ├── login.html
    ├── dashboard.html
    ├── user/{list,form}.html
    ├── incident/{list,form,detail,close}.html
    └── fragments/{header,footer}.html
```

---

## API endpoints

Everything is form-encoded, session-cookie-auth. **There is no JSON/REST API** — this is a server-rendered Thymeleaf app. Endpoints listed below are what backs each page/action.

### Public

| Method | Path | Notes |
|---|---|---|
| GET | `/login` | Login page |
| POST | `/login` | Form submit. Body: `username`, `password`, `_csrf` |

### Auth (any signed-in user)

| Method | Path | Notes |
|---|---|---|
| POST | `/logout` | Body: `_csrf` |
| GET | `/` | Redirects to `/dashboard` |
| GET | `/dashboard` | Role-aware (placeholder until phase 8) |
| GET | `/incidents` | Role-scoped: REPORTER sees own, others see all |
| GET | `/incidents/new` | New incident form |
| POST | `/incidents` | Body: `title`, `description`, `category`, `priority`, `_csrf` |
| GET | `/incidents/{id}` | Detail page (REPORTER blocked from others' incidents — 403) |
| POST | `/incidents/{id}/comment` | Body: `text`, `_csrf`. Blocked when status is CLOSED |

### Engineer or Admin only

| Method | Path | Notes |
|---|---|---|
| POST | `/incidents/{id}/assign` | Body: `assigneeId` (or empty to unassign), `_csrf` |
| POST | `/incidents/{id}/status` | Body: `status` (OPEN / IN_PROGRESS / RESOLVED), `_csrf`. CLOSED rejected — use `/close` |
| GET | `/incidents/{id}/close` | Close form |
| POST | `/incidents/{id}/close` | Body: `resolutionNotes`, `_csrf` |

### Admin only

| Method | Path | Notes |
|---|---|---|
| GET | `/users` | User list |
| GET | `/users/new` | New user form |
| POST | `/users` | Body: `username`, `password`, `role`, `fullName`, `email`, `enabled`, `_csrf` |
| GET | `/users/{id}/edit` | Edit user form |
| POST | `/users/{id}` | Body: same as create. Empty `password` keeps existing |
| POST | `/users/{id}/delete` | Body: `_csrf`. Refuses self-delete and last-admin-delete |
| POST | `/incidents/{id}/delete` | Body: `_csrf`. Cascades to comments |

---

## Testing the endpoints

The easiest way is **the browser**: log in, click around. Below are curl recipes for scripted testing — note that every state-changing request needs (a) the session cookie from login and (b) a CSRF token harvested from the page that contains the form.

### One-time login (saves session cookie)

```bash
COOKIES=/tmp/iq.txt
CSRF=$(curl -s -c $COOKIES http://localhost:8080/login \
  | grep -oE 'name="_csrf" value="[^"]+"' \
  | sed -E 's/.*value="([^"]+)".*/\1/')

curl -s -b $COOKIES -c $COOKIES -X POST http://localhost:8080/login \
  -d "username=admin&password=admin123&_csrf=$CSRF" \
  -o /dev/null -w "%{http_code} -> %{redirect_url}\n"
# 302 -> http://localhost:8080/dashboard
```

### Create an incident (as the logged-in user)

```bash
# Grab CSRF from the new-incident form page
CSRF=$(curl -s -b $COOKIES http://localhost:8080/incidents/new \
  | grep -oE 'name="_csrf" value="[^"]+"' \
  | head -1 \
  | sed -E 's/.*value="([^"]+)".*/\1/')

curl -s -b $COOKIES -X POST http://localhost:8080/incidents \
  --data-urlencode "title=Test incident" \
  --data-urlencode "description=Created via curl" \
  --data-urlencode "category=APPLICATION" \
  --data-urlencode "priority=P3" \
  --data-urlencode "_csrf=$CSRF" \
  -o /dev/null -w "%{http_code} -> %{redirect_url}\n"
```

### List incidents

```bash
curl -s -b $COOKIES http://localhost:8080/incidents -o /tmp/list.html -w "%{http_code}\n"
grep -oE 'INC-[0-9]+' /tmp/list.html | sort -u
```

### Add a comment to incident #1

```bash
CSRF=$(curl -s -b $COOKIES http://localhost:8080/incidents/1 \
  | grep -oE 'name="_csrf" value="[^"]+"' | head -1 \
  | sed -E 's/.*value="([^"]+)".*/\1/')

curl -s -b $COOKIES -X POST http://localhost:8080/incidents/1/comment \
  --data-urlencode "text=Investigating the issue." \
  --data-urlencode "_csrf=$CSRF" \
  -o /dev/null -w "%{http_code}\n"
```

### Logout

```bash
CSRF=$(curl -s -b $COOKIES http://localhost:8080/dashboard \
  | grep -oE 'name="_csrf" value="[^"]+"' | head -1 \
  | sed -E 's/.*value="([^"]+)".*/\1/')

curl -s -b $COOKIES -X POST http://localhost:8080/logout \
  -d "_csrf=$CSRF" -o /dev/null -w "%{http_code}\n"
```

### Postman / Insomnia

If you prefer a GUI:
- Enable **cookie jar** for `localhost`
- Send `POST /login` first (form-encoded body), then any subsequent request will reuse the session
- For each form POST, do a GET on the page that contains the form first, scrape the `_csrf` value out of the HTML, and include it in the next POST

A REST-style JSON API is intentionally NOT exposed — this is a Thymeleaf-rendered app where security relies on session cookies + CSRF.

---

## Data model

```
app_user            (id, username, password [BCrypt], role, full_name, email, enabled, created_at)
incident            (id, title, description, category, category_ai_suggestion,
                     priority, priority_ai_suggestion, status, reporter_id, assignee_id,
                     resolution_notes, ai_summary, created_at, updated_at, resolved_at)
incident_update     (id, incident_id, author_id, text, created_at)
ai_call_log         (id, call_type, prompt_summary, response_summary, latency_ms, success,
                     error_msg, created_at)
```

Notable indexes:
- FULLTEXT on `incident(title, description)` — used for similar-incident retrieval (phase 7)
- BTREE on `incident.status`, `incident.reporter_id`, `incident.assignee_id`

---

## Roles

| Capability | ADMIN | ENGINEER | REPORTER |
|---|---|---|---|
| Create incident | yes | yes | yes |
| View own incidents | yes | yes | yes |
| View others' incidents | yes | yes | no (403) |
| Comment on visible incident (when not CLOSED) | yes | yes | yes |
| Assign / change status | yes | yes | no |
| Close incident (with resolution) | yes | yes | no |
| Delete incident | yes | no | no |
| Manage users | yes | no | no |
| Use AI resolution-suggest / thread-summary | yes | yes | no (phase 7) |

---

## Build phases (where the project is now)

| Phase | Scope | Status |
|---|---|---|
| 1 | Maven skeleton, properties split | done |
| 2 | Entities, FULLTEXT schema | done |
| 3 | Spring Security + login + admin seed | done |
| 4 | User CRUD (admin) | done |
| 5 | Incident CRUD (no AI) | done |
| 6 | AiService + Gemini client + AiCallLog | next |
| 7 | AI features wired into incident flow | pending |
| 8 | Dashboard with role-scoped metrics | pending |
| 9 | PDF report (iText 7) | pending |
| 10 | Custom error pages, polish | pending |

---

## Troubleshooting

- **`mvn` not found:** install with `brew install maven`. If installed, `which mvn` must resolve.
- **`Communications link failure` on boot:** MySQL isn't running. `brew services start mysql`.
- **`Access denied for user 'root'`:** wrong password in `application.properties`. Empty string is correct if you used Homebrew defaults and never set one.
- **Lombok errors `cannot find symbol getId/builder`:** annotation-processor path missing. Already configured in `pom.xml` (`maven-compiler-plugin > annotationProcessorPaths`). If you removed it, restore.
- **Lombok crash `TypeTag :: UNKNOWN`:** you're on JDK 23+ but Lombok bundled by Spring Boot 3.3 doesn't support it. Run on JDK 17 (set `JAVA_HOME` accordingly).
- **AI calls failing with 401/403:** check `app.gemini.api-key` is correct (must start with `AIza`).
