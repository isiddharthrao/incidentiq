# IncidentIQ — Viva Questions & Answers

A practice document for the project viva. Organized by topic, easy → harder. Each Q&A includes a short answer (1-2 sentences) and follow-up depth in case the examiner pushes.

> **Tip for the viva:** answer the question that was asked, then stop. Don't volunteer extra info that opens unnecessary lines of attack. If you're not sure, say "I'd have to check" — confidently.

---

## Section 1 — Project overview

### Q1.1 What is IncidentIQ?
**Short:** A web-based IT/DevOps incident management system inspired by PagerDuty/Jira-Service-Desk, with AI features powered by Google Gemini.

**Extended:** Engineers can report and track incidents (e.g., "API gateway returning 502"). The system uses Gemini to auto-categorize incidents on creation, suggest priority, find similar past incidents (via MySQL FULLTEXT), suggest resolution steps using top-3 similar incidents as RAG context, and summarize the full thread when an incident is closed. Three roles (Admin / Engineer / Reporter) with role-scoped views.

### Q1.2 What problem does it solve?
**Short:** Faster triage and resolution by leveraging historical knowledge — engineers don't have to remember how a similar past incident was resolved.

### Q1.3 Walk me through the architecture.
**Short:** Layered: Controller → Service → Repository → Entity, plus a separate AI layer.

**Extended:**
- **Controllers** (Thymeleaf-rendered): `AuthController`, `DashboardController`, `UserController`, `IncidentController`, `AiAdminController`, `ErrorPageController`
- **Services**: `UserService`, `IncidentService`, `DashboardService`, `AiService`, `PdfService`
- **Repositories** (Spring Data JPA): `UserRepository`, `IncidentRepository`, `IncidentUpdateRepository`, `AiCallLogRepository`
- **Entities**: `User`, `Incident`, `IncidentUpdate`, `AiCallLog` + enums (`Role`, `Priority`, `Category`, `IncidentStatus`)
- **AI layer**: `GeminiClient` (REST wrapper) + `PromptTemplates` + `AiResponseParser` — only `GeminiClient` talks to Gemini; everything else is plain Java
- **PDF**: `IncidentReportGenerator` using iText 7
- **Cross-cutting**: `SecurityConfig`, `GlobalExceptionHandler`, `AdminSeeder`

---

## Section 2 — Tech stack rationale

### Q2.1 Why Java 17, not 8 or 21?
**Short:** 17 is the current LTS that Spring Boot 3.x targets. 8 is too old for Spring Boot 3 (which requires Java 17 minimum); 21 is also LTS but 17 has wider library support.

### Q2.2 Why Spring Boot 3.x?
**Short:** It's the current production version, brings Jakarta EE 9+ namespaces (`jakarta.persistence` instead of `javax.persistence`), and includes Spring Security 6 with form login + CSRF out of the box.

### Q2.3 Why Thymeleaf, not React/Vue?
**Short:** Server-side rendering is simpler for a college project — no separate frontend build, no CORS, no JWT. Single deployable.

**Extended follow-up:** "Wouldn't React be more impressive?" — *"For an admin dashboard with form-heavy workflows, server-rendered HTML is faster to build and more secure (CSRF + session is simpler than JWT). React would be the right call if we needed real-time updates or rich interactivity."*

### Q2.4 Why MySQL, not PostgreSQL?
**Short:** Spec required MySQL. MySQL's InnoDB FULLTEXT index works well for our similar-incident search. PostgreSQL has stronger full-text search (`tsvector`) but MySQL is simpler to set up.

### Q2.5 Why Lombok?
**Short:** Reduces boilerplate (`@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`). Zero runtime overhead — it's compile-time annotation processing.

**Watch-out:** *"Why not `@Data` on entities?"* — `@Data` generates `equals`, `hashCode`, `toString`, all of which trigger lazy loading on bidirectional JPA relationships and can infinite-loop. Use `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` only.

### Q2.6 Why Maven, not Gradle?
**Short:** Spring Boot's documentation defaults to Maven; college references and StackOverflow answers assume Maven. Gradle is faster but Maven is more discoverable.

---

## Section 3 — Spring Security

### Q3.1 How does authentication work?
**Short:** Form-based login. Spring Security's `UsernamePasswordAuthenticationFilter` intercepts `POST /login`, validates credentials against `CustomUserDetailsService`, creates an `HttpSession` with `JSESSIONID` cookie. Subsequent requests are authenticated via that cookie.

### Q3.2 Why session-based, not JWT?
**Short:** Server-rendered Thymeleaf needs no separate API; sessions are simpler and more secure (HttpOnly + Secure flags, server-side invalidation on logout, no token-leak surface).

### Q3.3 What's `CustomUserDetailsService`?
**Short:** A bridge between our `User` entity and Spring Security's `UserDetails` contract.

**Extended:** Spring Security's `AuthenticationManager` calls `userDetailsService.loadUserByUsername(name)` and expects a `UserDetails`. Our service queries `UserRepository`, wraps the `User` in a `CustomUserDetails`, returns it. We deliberately don't make the entity itself implement `UserDetails` — that would couple `entity/` to Spring Security.

### Q3.4 How is the password stored?
**Short:** BCrypt hash (`$2a$10$…`), 60 chars. Cost factor 10 (default).

**Extended:** `BCryptPasswordEncoder` is registered as a bean in `SecurityConfig`. `AdminSeeder` and `UserService` both use it. The DB column is `VARCHAR(100)` to fit comfortably with headroom. Strong viva note: BCrypt automatically generates a per-password salt and embeds it — no separate salt column needed.

### Q3.5 What is CSRF? How is it handled?
**Short:** Cross-Site Request Forgery — an attacker tricks an authenticated user's browser into submitting a form to your site. Spring Security adds a hidden `_csrf` token to every form; submissions without it are rejected.

**Extended:** Thymeleaf's Spring dialect auto-injects the token into `<form th:action="...">`. For our AJAX call to `/incidents/ai/suggest`, we read the token from a `<meta>` tag and send it as `X-CSRF-TOKEN` header — Spring Security's default `CsrfFilter` accepts both forms.

### Q3.6 What happens if you disable CSRF?
**Short:** Any authenticated user could be tricked into POSTing to dangerous endpoints (e.g., delete user) just by visiting an attacker's page that auto-submits a hidden form. Don't disable it.

### Q3.7 How are roles enforced?
**Short:** Two layers — URL rules in `SecurityConfig` (`requestMatchers("/users/**").hasRole("ADMIN")`) and `@PreAuthorize` on controller methods. Defense in depth.

### Q3.8 Why the `ROLE_` prefix?
**Short:** Spring Security convention. `hasRole("ADMIN")` actually checks for `ROLE_ADMIN` authority. We add the prefix in `CustomUserDetails.getAuthorities()`.

### Q3.9 What does `@EnableMethodSecurity` do?
**Short:** Enables `@PreAuthorize`, `@PostAuthorize`, `@Secured` annotations on methods. Without it, those annotations are silently ignored.

### Q3.10 What's the session creation policy?
**Short:** `IF_REQUIRED` (Spring default) — session is created on login, reused thereafter. Alternative is `STATELESS` (used with JWT), which we don't need.

### Q3.11 How does logout work?
**Short:** `POST /logout` with CSRF token. Spring Security invalidates the `HttpSession`, clears the security context, deletes the `JSESSIONID` cookie, redirects to `/login?logout`.

### Q3.12 Could I bypass security by hitting the URL directly?
**Short:** No. Every URL except `/login`, `/css/**`, `/error/**` requires authentication. Spring Security's filter chain runs before any controller.

---

## Section 4 — Spring Data JPA / Hibernate

### Q4.1 How is the database schema created?
**Short:** Hibernate auto-creates tables from `@Entity` classes (`spring.jpa.hibernate.ddl-auto=update`). For things Hibernate can't generate (the FULLTEXT index), we use a `schema.sql` that runs after Hibernate.

### Q4.2 Why `ddl-auto=update` and not Flyway?
**Short:** Simpler for college; Hibernate looks at entity annotations and adds missing columns/tables. In production, you'd use Flyway or Liquibase migrations because `ddl-auto` won't drop or modify existing columns and can drift.

### Q4.3 What is `open-in-view`?
**Short:** A Spring Boot default that keeps a Hibernate session open during template rendering, so lazy collections can be loaded from the view. We disabled it (`open-in-view=false`) — anti-pattern that hides N+1 queries.

**Extended (likely follow-up):** *"What problem does this cause?"* — In `IncidentRepository`, we needed `@EntityGraph(attributePaths = {"reporter", "assignee"})` on the list query because the template accesses `incident.reporter.username`. Without OSIV, that lazy access fails. With OSIV, it would silently work but each row would trigger an extra SQL query.

### Q4.4 What's `@EntityGraph`?
**Short:** Tells Spring Data JPA which lazy associations to fetch eagerly for a specific query. Generates a `LEFT JOIN FETCH` under the hood.

### Q4.5 Why bidirectional `Incident ↔ IncidentUpdate`?
**Short:** Incident has `@OneToMany List<IncidentUpdate> updates` for thread display; IncidentUpdate has `@ManyToOne Incident incident` for the FK. Both sides need to be set when adding a comment — JPA doesn't auto-sync.

### Q4.6 What does `cascade=ALL` + `orphanRemoval=true` mean on `Incident.updates`?
**Short:** Saving the parent saves the children. Removing a child from the list deletes it. Deleting the parent deletes all children.

### Q4.7 What's `@Enumerated(EnumType.STRING)` and why not `EnumType.ORDINAL`?
**Short:** STRING stores the enum name (e.g., `"P1"`). ORDINAL stores the index (`0` for the first value). If you ever insert a new enum value in the middle of the declaration, ORDINAL silently corrupts existing data. STRING is the only safe choice.

### Q4.8 What's `@CreationTimestamp` / `@UpdateTimestamp`?
**Short:** Hibernate-managed timestamps. `@CreationTimestamp` is set once when the entity is first persisted; `@UpdateTimestamp` updates on every flush.

### Q4.9 Why does `User` map to `app_user` table?
**Short:** `user` is a reserved word/function in some databases (PostgreSQL, MySQL has `USER()` function). `app_user` is unambiguous.

### Q4.10 What's `@Transactional` doing?
**Short:** Wraps the method in a database transaction — all DB ops succeed atomically or roll back on exception. Spring AOP creates a proxy that opens a transaction before, commits/rolls back after.

### Q4.11 Why `@Transactional(readOnly = true)` on read methods?
**Short:** Hint to Hibernate to skip dirty-checking and to JDBC drivers to allow read replicas. Slight performance gain, plus self-documenting.

### Q4.12 What happens on duplicate username?
**Short:** `UserService.create` calls `userRepository.existsByUsername(...)` first, throws `DuplicateUsernameException`. If we skipped the check, MySQL's UNIQUE constraint would throw `DataIntegrityViolationException` from the SQL layer.

---

## Section 5 — AI integration (Gemini)

### Q5.1 How does the AI integration work?
**Short:** `GeminiClient` POSTs to Google's Generative Language API (`generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`) using Spring's `RestClient`. API key is in `application.properties`.

### Q5.2 Why direct REST and not Spring AI?
**Short:** Spring AI 1.0 GA didn't include a starter for Google AI Studio direct (only Vertex AI Gemini, which requires a full GCP project + service account). Direct REST is simpler — one HTTP call.

### Q5.3 What model are you using?
**Short:** `gemini-2.5-flash`. Configurable via `app.gemini.model` in `application.properties`.

**Extended:** Initial choice was `gemini-2.0-flash` but the account hit a free-tier quota issue. Switched to `gemini-2.5-flash` which is newer and uses a different quota pool.

### Q5.4 What if Gemini is down or the key is wrong?
**Short:** Every call returns `Optional<String>`. On failure, returns `Optional.empty()` and the caller decides what to do — usually "skip the AI suggestion" or "show an error message". The rest of the app keeps working.

### Q5.5 Where do you log AI calls?
**Short:** `ai_call_log` table — every call (success or failure) writes a row with `call_type`, `latency_ms`, `success`, `prompt_summary`, `response_summary`, `error_msg`. Demonstrable proof during viva that AI is being invoked.

### Q5.6 What's `PromptTemplates`?
**Short:** A single class holding all prompts as Java methods. Lets us version, A/B test, and tune wording without scattering string literals across the codebase.

### Q5.7 What's `AiResponseParser`?
**Short:** Translates Gemini's text response into our enums. Substring-matches enum names because the model sometimes adds prose like "Category: APPLICATION." Returns `Optional.empty()` if no match — caller falls back to OTHER/P3.

### Q5.8 Why do you store both `category` and `category_ai_suggestion`?
**Short:** Spec required preserving the AI's original suggestion even when the human overrides it. Lets you query "where did humans disagree with AI?" — a `WHERE category != category_ai_suggestion` check. Strong signal AI isn't just decoration.

### Q5.9 How does the create-form's AI button work?
**Short:** Vanilla `fetch()` POSTs `{title, description}` (JSON) to `/incidents/ai/suggest` with CSRF in `X-CSRF-TOKEN` header. Server returns `{category, priority}`. JS populates the dropdowns + hidden audit fields. User can override before submit.

### Q5.10 Why CSRF in a header for the JSON endpoint, but a hidden field for forms?
**Short:** Both work — Spring Security's `CsrfFilter` checks the parameter `_csrf` first, then headers like `X-CSRF-TOKEN`. JSON requests don't have form fields, so the header is the natural fit.

### Q5.11 Are AI calls synchronous?
**Short:** Yes — each call blocks the request thread for 2-8 seconds. Fine for an admin tool. Production would use async/streaming or a separate thread pool.

---

## Section 6 — RAG and FULLTEXT search

### Q6.1 Is the resolution-suggestion feature RAG?
**Short:** Yes, but lexical not semantic. We retrieve top-3 similar past incidents using MySQL FULLTEXT, stuff their resolution notes into the prompt as context, then ask Gemini.

**Important nuance to volunteer:** *"Real production RAG uses embedding similarity (e.g., pgvector, Pinecone). We're using lexical retrieval — word-overlap rather than meaning-overlap. It works for our scale and is the next thing I'd improve."*

### Q6.2 What is MySQL FULLTEXT?
**Short:** A specialized index on text columns. Lets you query with `MATCH(col1, col2) AGAINST ('query' IN NATURAL LANGUAGE MODE)`, which returns a relevance score per row.

### Q6.3 Show me the FULLTEXT query.
```sql
SELECT i.* FROM incident i
WHERE i.id != :excludeId
  AND MATCH(i.title, i.description) AGAINST (:query IN NATURAL LANGUAGE MODE) > 0
ORDER BY MATCH(i.title, i.description) AGAINST (:query IN NATURAL LANGUAGE MODE) DESC
```
The `> 0` filters non-matching rows. The `ORDER BY` re-evaluates relevance to rank by best-match. (MySQL's optimizer handles the dual MATCH cheaply.)

### Q6.4 Why is the FULLTEXT index in `schema.sql` and not in `@Entity`?
**Short:** Hibernate's `@Index` annotation can't generate FULLTEXT indexes. So we add it via `schema.sql` after Hibernate creates the table.

### Q6.5 What's `IN NATURAL LANGUAGE MODE`?
**Short:** Default mode. Treats the query as natural text — strips stopwords, ranks by word frequency / inverse document frequency. Alternatives: `BOOLEAN MODE` (supports +/- operators) and `WITH QUERY EXPANSION` (rerun with top results as new query).

### Q6.6 What are the gotchas with FULLTEXT?
**Short:** Default minimum word length is 4 chars in InnoDB (`innodb_ft_min_token_size`). Words shorter (e.g., "API") are ignored. Stopwords like "the", "a" are also ignored. So very short queries can return empty results.

### Q6.7 Why role-restrict similar incidents to engineer/admin?
**Short:** Privacy. Reporters can only see their own incidents. The "similar" panel could leak titles/resolutions from other reporters' incidents. Hiding it for reporters maintains the visibility model.

---

## Section 7 — PDF generation (iText 7)

### Q7.1 Why iText 7?
**Short:** Spec required it. Battle-tested PDF library with rich layout primitives (Document, Paragraph, Table, Cell, Div). Community Edition is AGPL.

### Q7.2 What's the AGPL license imply?
**Short:** Free to use, but if you distribute the application or run it as a SaaS, you must release source code. For academic use that doesn't apply.

### Q7.3 How does the PDF endpoint work?
**Short:** `GET /incidents/{id}/pdf` → `PdfService.generateIncidentReport` → calls `IncidentService.findVisibleDetail` (visibility check) → `IncidentReportGenerator.generate` produces `byte[]` → controller returns with `Content-Type: application/pdf` and `Content-Disposition: attachment; filename="..."`.

### Q7.4 How does iText render the PDF?
**Short:** `PdfWriter` writes to a `ByteArrayOutputStream`, wrapped in `PdfDocument` (low-level), wrapped in `Document` (high-level layout). We call `document.add(...)` with `Paragraph`, `Table`, `Div` elements. Auto-handles page breaks.

### Q7.5 Why is the PDF compressed?
**Short:** iText writes content streams using FlateDecode (zlib). Default behavior — saves bytes. That's why grep on the raw bytes can't find words; you need a real PDF parser like pypdf.

### Q7.6 Where is `IncidentReportGenerator` placed in the package structure?
**Short:** `com.incidentmgmt.pdf` — separate from `service/` because it's a layout/presentation concern (specific to PDF), not business logic. `PdfService` is the thin facade in `service/` that combines visibility check + generation.

---

## Section 8 — Dashboard and metrics

### Q8.1 How is the dashboard role-scoped?
**Short:** `DashboardService.compute(currentUser)` branches on role. REPORTER queries are filtered by `reporter_id = me`. ENGINEER and ADMIN see org-wide. ADMIN gets extra metrics (total users, AI call counts).

### Q8.2 How is the average resolution time calculated?
**Short:** `AVG(TIMESTAMPDIFF(MINUTE, created_at, resolved_at))` for incidents where `resolved_at` falls in the time window. JPQL has no `TIMESTAMPDIFF`, so it's a native query.

### Q8.3 What's the activity feed?
**Short:** Last 10 events: incident-created (from `incident.created_at`) and comment-added (from `incident_update.created_at`). Fetched separately, merged in Java, sorted by timestamp.

### Q8.4 Why CSS bars instead of a chart library?
**Short:** Spec said no chart library. Each bar's width is `(value / max) × 100%`. Color matches the priority/status palette. Total CSS for the bars is ~30 lines.

### Q8.5 Why pre-fill the priority/status maps with zeros?
**Short:** SQL `GROUP BY` only returns groups that have rows. If no `P1` incidents exist, the result lacks a P1 entry. We pre-fill `EnumMap` so the dashboard always shows all 4 priorities, even if they're zero. Predictable rendering.

---

## Section 9 — Error handling

### Q9.1 How are 403 errors handled?
**Short:** Two paths:
- **From `@PreAuthorize`**: Spring Security's filter chain catches `AccessDeniedException` and uses the configured `accessDeniedPage("/error/403")`.
- **From service code**: `GlobalExceptionHandler` (`@ControllerAdvice`) catches `AccessDeniedException` and returns view `error/403` with status 403.

Both paths render `templates/error/403.html`.

### Q9.2 How are 404 errors handled?
**Short:** `EntityNotFoundException` thrown by services → `GlobalExceptionHandler` returns view `error/404` with status 404. Spring Boot's `BasicErrorController` also handles raw 404s (e.g., URL doesn't match any route) using the same template.

### Q9.3 What's the AI-unavailable page for?
**Short:** A friendly fallback if Gemini is completely unreachable. Currently navigable manually (`/error/ai-unavailable`); not wired into the auto-flow because we already gracefully degrade per-call. A useful reassurance page for users who saw an "AI failed" message.

---

## Section 10 — Common follow-up questions

### Q10.1 What would you improve next?
- Embedding-based similarity search (replacing FULLTEXT with `pgvector` or external vector DB)
- Async AI calls with WebSocket streaming so the UI doesn't block 5+ seconds on resolution suggestions
- Real activity log table (capture status changes, assignments, closes — not just creates and comments)
- Demote-self-to-non-admin guard in `UserService.update` (we have it on delete but not update)
- Production-grade DB migrations (Flyway) instead of `ddl-auto=update`
- Observability (Micrometer + Prometheus + Grafana) for AI call latencies and failure rates

### Q10.2 Walk me through what happens when I click "Close incident".
1. `GET /incidents/{id}/close` (closeForm) renders the resolution-notes form.
2. User submits; `POST /incidents/{id}/close` hits `IncidentController.close`.
3. `@PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")` guards the call.
4. `IncidentService.close(id, notes)` runs in a `@Transactional`:
   - Loads incident with `findByIdWithDetails` (eager: reporter, assignee, updates, authors)
   - Sets `status = CLOSED`, `resolutionNotes = notes`, `resolvedAt = now`
   - Calls `aiService.summarizeThread(incident)`
   - Stores returned summary in `incident.aiSummary` (or skips if AI failed)
5. `aiService.summarizeThread` calls `geminiClient.ask("THREAD_SUMMARY", prompt)`:
   - Builds prompt from `PromptTemplates.summarizeThread(incident)`
   - POSTs to Gemini REST API
   - Writes a row to `ai_call_log` (success or failure)
6. Controller redirects to `/incidents/{id}` with flash "Incident closed."
7. Detail page re-renders. The `<section th:if="${incident.aiSummary}">` block now shows.

### Q10.3 What's the most surprising bug you ran into?
**Lazy-init exception on the incident list page.** I'd added `@EntityGraph` to the detail query early but forgot the list query. Phase 2 entities silently worked because Hibernate uses reflection at the SQL layer — never actually called the Lombok-generated getters. Phase 3 didn't trip it either. Phase 5's list-rendering Thymeleaf template was the first place to actually access `incident.reporter.username` lazily, and `open-in-view=false` blew up. Fix: add `@EntityGraph(attributePaths = {"reporter", "assignee"})` to the list queries too. Strong viva point: caught at the right layer — exactly the kind of issue OSIV-off is meant to expose.

### Q10.4 Why didn't you use a microservice architecture?
**Short:** Microservices solve scale, deployment, and team-size problems we don't have. A single Spring Boot monolith is the right tool for a college project. Splitting it would add operational cost (multiple deploys, network calls, distributed transactions) for zero benefit.

### Q10.5 How would you scale this for 1M incidents?
- Add read replicas — most queries are read-heavy
- Move FULLTEXT to a dedicated search service (Elasticsearch / OpenSearch) for richer queries
- Replace lexical similarity with embedding-based ANN search (pgvector or vector DB)
- Cache hot incidents (assigned-to-me lists, dashboard counts) in Redis with short TTL
- Batch-async AI calls (queue + worker pool) for resolution suggestions instead of sync
- Move PDF generation to a worker service so the request thread isn't blocked
- Database partitioning on `created_at` for archival of old incidents

### Q10.6 How do you test the AI features?
**Short:** Honest answer — manually. The AI calls are non-deterministic, so unit tests against the live API are flaky. For real testing you'd mock `GeminiClient` to return canned responses, then test that `AiService` parses and stores them correctly. The `AiResponseParser` is the only deterministic AI-related class and is the easiest to unit-test.

### Q10.7 If you had to add real-time updates, how?
**Short:** Spring's `WebSocket` support + STOMP, or `Server-Sent Events` for one-way push. New comment posted → server publishes to a topic per incident → connected clients refresh. Adds complexity but doable. For viva: *"Out of scope for this project, but the right pattern is server-pushed events on a per-incident topic."*

### Q10.8 What's the biggest weakness of your design?
- **No retry on AI calls.** If Gemini returns a transient 429, we log and give up. Retrying with backoff would improve success rate.
- **No rate limiting.** A reporter could spam `/incidents/ai/suggest` and exhaust the Gemini free-tier quota for everyone. Bucket4j or per-user counters would fix.
- **AI summary blocks the close request.** ~3-second latency that the user has to wait through. Making it async would feel snappier.
- **FULLTEXT only indexes English-ish text.** A Portuguese or Hindi description wouldn't match well; embedding-based retrieval would.

### Q10.9 What did you learn?
**Pick a real one.** Examples that match this project:
- "How `open-in-view=false` exposes lazy-loading bugs at the right layer rather than hiding them."
- "Why prompt engineering matters — early prompts returned prose like 'Category: APPLICATION.' which broke parsing until I added explicit 'EXACTLY one of these tokens, no other text' instructions."
- "How to design for graceful degradation — having every AI call return `Optional<String>` meant the rest of the app didn't have to know AI exists."
- "The difference between authentication and authorization, and where each lives in Spring Security's filter chain."

---

## Section 11 — Quick-fire one-liners

| Question | Answer |
|---|---|
| What's the default admin password? | `admin` / `admin123` (seeded by `AdminSeeder` on first boot) |
| Where's the API key? | `application.properties`, `app.gemini.api-key=...`. Gitignored. |
| How many tables? | 4 — `app_user`, `incident`, `incident_update`, `ai_call_log` |
| How many roles? | 3 — ADMIN, ENGINEER, REPORTER |
| How many AI features? | 4 — auto-categorize, auto-priority, similar incidents (RAG-backed resolution suggestion), thread summary on close |
| Server port? | 8080 |
| Database? | MySQL (running on 3306, schema `incident_iq` auto-created) |
| What ORM? | Hibernate (via Spring Data JPA) |
| What's the bill number / incident ID format? | `INC-{id}` where `{id}` is the auto-increment from MySQL |
| What's the PDF filename format? | `incident-report-INC-{id}.pdf` |
| Do reporters see other people's incidents? | No — service throws `AccessDeniedException` → 403 page |
| Can engineers delete incidents? | No, only admins |
| Can admins delete themselves? | No — guarded in `UserService.delete` |
| What's the prompt template class? | `com.incidentmgmt.ai.PromptTemplates` |
| What's the FULLTEXT index name? | `idx_incident_fts` on `incident(title, description)` |

---

## Final viva tips

1. **Don't memorize answers.** Understand the *why* behind each decision so you can reason from first principles when asked an unexpected question.
2. **Volunteer the right amount.** Answer the question, then a one-sentence "the alternative would be X but it's overkill here" — shows you considered options. Don't go on long tangents.
3. **If you don't know something, say so.** "I'd have to check" or "I'm not sure but my guess is..." is far better than a confident-sounding wrong answer.
4. **Have the demo ready.** Open the app, log in as admin, have at least one closed incident with an AI summary. Be able to show the `ai_call_log` table in MySQL CLI in case they ask "show me where AI is being called."
5. **Lean on the audit columns.** The `category_ai_suggestion` vs `category` columns and the `ai_call_log` table are the strongest "this AI is real, not theatre" signals you have. Use them.

Good luck.
