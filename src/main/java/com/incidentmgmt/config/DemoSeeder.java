package com.incidentmgmt.config;

import com.incidentmgmt.entity.*;
import com.incidentmgmt.repository.IncidentRepository;
import com.incidentmgmt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds demo users and incidents on a fresh DB so screenshots / viva demos
 * don't show an empty dashboard. Runs only when:
 *   - exactly one user exists (the admin AdminSeeder created), AND
 *   - no incidents exist yet
 *
 * Re-running the app does nothing once data is present.
 */
@Component
@Order(2) // after AdminSeeder
@RequiredArgsConstructor
@Slf4j
public class DemoSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() != 1 || incidentRepository.count() > 0) {
            return;
        }

        log.info("Seeding demo users and incidents...");

        User alice = createUser("alice", "alice123", Role.ENGINEER, "Alice Wong", "alice@incidentiq.local");
        User bob = createUser("bob", "bob123", Role.ENGINEER, "Bob Smith", "bob@incidentiq.local");
        User charlie = createUser("charlie", "charlie123", Role.REPORTER, "Charlie Davis", "charlie@incidentiq.local");
        User diana = createUser("diana", "diana123", Role.REPORTER, "Diana Patel", "diana@incidentiq.local");

        // 1 — CLOSED, P1 INFRASTRUCTURE, AI agreed
        seedIncident(
                "Production web servers OOMing under traffic spike",
                "Multiple production web nodes hitting OOM after 14:00. Memory climbing steadily for 30 minutes before crash. Auto-scaling didn't kick in. Affecting 80% of users with 503 errors.",
                Category.INFRASTRUCTURE, Category.INFRASTRUCTURE,
                Priority.P1, Priority.P1,
                IncidentStatus.CLOSED,
                charlie, alice,
                "Identified memory leak in JSON deserializer for product-catalog endpoint. Patched and redeployed. Increased baseline node count from 4 to 6 to absorb similar spikes.",
                "An production outage was triggered by web servers hitting OOM during a traffic spike, causing 503 errors for 80% of users. Investigation revealed a memory leak in the JSON deserializer for the product-catalog endpoint. The team deployed a patch to fix the leak and increased baseline node count to 6 to better absorb future spikes.",
                List.of(
                        comment(alice, "On it. Pulling heap dumps from one of the affected nodes."),
                        comment(alice, "Found a retained reference in the JSON deserializer. Memory grows linearly with traffic. Patching now."),
                        comment(bob, "Patch deployed and verified. Memory is flat. Marking resolved.")
                )
        );

        // 2 — RESOLVED, P2 DATABASE, AI suggested APPLICATION (override demo)
        seedIncident(
                "Slow queries on user_profile table killing API latency",
                "P95 latency on /api/users went from 80ms to 4s after last week's deploy. Slow query log shows full table scans on user_profile.",
                Category.APPLICATION, Category.DATABASE,
                Priority.P2, Priority.P2,
                IncidentStatus.RESOLVED,
                charlie, alice,
                "Missing index on user_profile.tenant_id. Added index, p95 dropped to 90ms. EXPLAIN now shows index range scan.",
                null,
                List.of(
                        comment(alice, "Confirmed full table scans. Tenant_id was added in the last migration but no index. Adding now."),
                        comment(alice, "Index added in production. Latency back to normal.")
                )
        );

        // 3 — IN_PROGRESS, P1 NETWORK
        seedIncident(
                "VPN connectivity intermittent for remote engineering team",
                "Engineers in EU region reporting VPN drops every 5-10 minutes since this morning. Cisco AnyConnect logs show TLS handshake failures. Affecting ~30 engineers, blocking deploys.",
                Category.NETWORK, Category.NETWORK,
                Priority.P1, Priority.P1,
                IncidentStatus.IN_PROGRESS,
                diana, bob,
                null, null,
                List.of(
                        comment(bob, "Reproduced from my laptop. Looking at gateway logs."),
                        comment(bob, "Suspect a faulty cert rotation on the EU gateway. Rolling back the cert now.")
                )
        );

        // 4 — OPEN, P3 APPLICATION, AI suggested P2 (priority override demo)
        seedIncident(
                "Dashboard pie chart not rendering on Safari mobile",
                "Charts on the analytics dashboard render fine on Chrome but show as blank on Safari iOS 17. No JS console errors visible. Reproduces consistently.",
                Category.APPLICATION, Category.APPLICATION,
                Priority.P2, Priority.P3,
                IncidentStatus.OPEN,
                charlie, null,
                null, null,
                List.of()
        );

        // 5 — CLOSED, P2 SECURITY, AI summary written
        seedIncident(
                "Suspicious login attempts from foreign IPs on admin accounts",
                "WAF flagged 200+ failed login attempts on /login over 10 minutes, all from a single Russian IP block targeting admin accounts. No successful logins observed.",
                Category.SECURITY, Category.SECURITY,
                Priority.P2, Priority.P2,
                IncidentStatus.CLOSED,
                diana, bob,
                "Blocked the offending IP block at the WAF. Enabled rate limiting on /login (5 attempts per IP per minute). Forced password reset for all admin accounts as precaution.",
                "A targeted brute-force login attempt against admin accounts was detected by the WAF, originating from a Russian IP block with 200+ failed attempts in 10 minutes. The team blocked the IP block at the WAF, enabled rate limiting on the login endpoint, and forced a password reset for all admin accounts. No successful unauthorized logins occurred.",
                List.of(
                        comment(bob, "Pulled the IP block list from the WAF logs. None matched any legitimate user. Blocking."),
                        comment(alice, "Rate limit deployed. Forcing admin password reset as precaution.")
                )
        );

        // 6 — OPEN, P4 OTHER (cosmetic)
        seedIncident(
                "Feature request: dark mode for the engineer console",
                "Several engineers have asked for a dark theme. Currently only light mode is available. Not urgent but a frequently-requested polish item.",
                Category.OTHER, Category.OTHER,
                Priority.P4, Priority.P4,
                IncidentStatus.OPEN,
                diana, null,
                null, null,
                List.of(
                        comment(diana, "Adding to the polish backlog. Will pick up after the Q1 release.")
                )
        );

        // 7 — RESOLVED, P3 DEPLOYMENT
        seedIncident(
                "Staging deploy fails on flaky integration test",
                "Pipeline 'staging-deploy' fails ~30% of the time on test 'OrderRepositoryIT.shouldHandleConcurrentWrites'. Test passes locally and on retry. Blocking nightly staging deploys.",
                Category.DEPLOYMENT, Category.DEPLOYMENT,
                Priority.P3, Priority.P3,
                IncidentStatus.RESOLVED,
                charlie, alice,
                "Test was relying on system-clock precision below 1ms which is flaky on CI VMs. Refactored to use a controllable Clock bean. 50 runs green.",
                null,
                List.of(
                        comment(alice, "Refactored the test to inject a controllable Clock. Re-running the pipeline 20 times to verify.")
                )
        );

        // 8 — IN_PROGRESS, P2 APPLICATION
        seedIncident(
                "Search returning duplicate results after Elasticsearch reindex",
                "Site search is returning each product 2-3 times since this morning's reindex. Affects all queries. Suspect indexing job ran twice without cleaning the previous index.",
                Category.APPLICATION, Category.APPLICATION,
                Priority.P2, Priority.P2,
                IncidentStatus.IN_PROGRESS,
                charlie, bob,
                null, null,
                List.of(
                        comment(bob, "Confirmed the indexing CronJob ran twice (cron and a manual trigger overlapped). Cleaning the index and re-running.")
                )
        );

        log.info("Demo data seeded: {} users, {} incidents.",
                userRepository.count(), incidentRepository.count());
    }

    private User createUser(String username, String password, Role role, String fullName, String email) {
        User u = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role(role)
                .fullName(fullName)
                .email(email)
                .enabled(true)
                .build();
        return userRepository.save(u);
    }

    private void seedIncident(String title, String description,
                              Category aiCategory, Category finalCategory,
                              Priority aiPriority, Priority finalPriority,
                              IncidentStatus status,
                              User reporter, User assignee,
                              String resolutionNotes, String aiSummary,
                              List<CommentSeed> comments) {
        Incident i = Incident.builder()
                .title(title)
                .description(description)
                .categoryAiSuggestion(aiCategory)
                .category(finalCategory)
                .priorityAiSuggestion(aiPriority)
                .priority(finalPriority)
                .status(status)
                .reporter(reporter)
                .assignee(assignee)
                .resolutionNotes(resolutionNotes)
                .aiSummary(aiSummary)
                .resolvedAt(status == IncidentStatus.RESOLVED || status == IncidentStatus.CLOSED
                        ? LocalDateTime.now() : null)
                .build();
        for (CommentSeed c : comments) {
            IncidentUpdate u = IncidentUpdate.builder()
                    .incident(i)
                    .author(c.author())
                    .text(c.text())
                    .build();
            i.getUpdates().add(u);
        }
        incidentRepository.save(i);
    }

    private CommentSeed comment(User author, String text) {
        return new CommentSeed(author, text);
    }

    private record CommentSeed(User author, String text) {}
}
