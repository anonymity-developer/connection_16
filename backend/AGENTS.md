# Backend implementation

- Follow root AGENTS.md. Use Java 21, existing Spring Boot/Gradle Wrapper, Jakarta validation, Spring Security, JPA, Flyway and Redis session configuration.
- Read `docs/auth.md` before authentication work. Login is Mattermost username + TAG ME password. DM OTP is signup/change/reset only and must bind purpose/account. Password changes must revoke current and other sessions through credential version checks.
- Current V1/V2 are applied baseline migrations. Add new migrations for usernames/credentials/consents and later domains; never rewrite existing migrations. Distinguish planned ERD from actual DDL.
- Keep controllers responsible for DTOs/HTTP and services for ownership, event period, unlock, slots, transactions and state. Never serialize JPA users/credentials directly to public JSON.
- Retain CSRF, safe session cookies, account/IP limits and explicit allowed public endpoints. Never trust body user IDs, roles, scores, types or completion flags.
- Use MySQL UNIQUE/PK as the final defense for one-time slots and relationships. Lock users before domain rows and the global completion sequence; when multiple users are needed lock their IDs ascending. Do not call external APIs while holding DB row locks.
- Keep content revision bodies immutable, pending/approved pointers consistent and moderation reprocessing idempotent. Save jobs and submissions transactionally, then call the external provider outside the transaction.
- Preserve server receipt time, original source and award snapshots. Never use AI approval processing order as first-finish award order.
- Use Testcontainers with actual MySQL/Redis for constraints, migrations and races. Do not replace those checks with H2 or silently skip when Docker is absent.
- Run `./gradlew test bootJar`; for persistence/concurrency/infrastructure changes also `./gradlew integrationTest`. Record blocked checks rather than claiming success. No production/test bypass using fixed OTPs or shared passwords.
