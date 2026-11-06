# TuoYu-Cloud

> A production-ready, open-source Java microservice framework: secure by default, verifiable quality, monolith and microservices from one codebase, cloud native, fully open source.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![JDK](https://img.shields.io/badge/JDK-21-green.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-blue.svg)
![Status](https://img.shields.io/badge/status-in%20development-orange.svg)

TuoYu-Cloud is a general-purpose framework for enterprise back-office systems. It ships with ready-to-use building blocks such as permissions, organizations, workflow, code generation, object storage, and message push. **The same codebase can be deployed as a monolith or as microservices**, so teams can evolve smoothly as their business grows.

> ⚠️ **Early development.** APIs and directory layout may still change; not recommended for production yet. The first public preview, `1.0.0-alpha.1`, is in progress.

## Goals

| Goal | Description |
|---|---|
| **Secure by default** | No plaintext secrets in the repository; in production, startup self-checks refuse to boot with weak passwords, default keys, or exposed sensitive endpoints |
| **Monolith and microservices in one** | In-process calls in monolith mode, Dubbo in microservice mode, with no changes to business code |
| **Pluggable multi-tenancy** | Off by default with zero intrusion; when enabled, it covers the database, cache, sessions, messaging, async and scheduled jobs |
| **Verifiable quality** | CI always runs the tests (no skipping), architecture rules are checked automatically, and database changes are managed with Flyway |
| **Cloud native** | Containerized deployment, health checks, tracing, graceful shutdown |
| **Fully open source** | MIT licensed, no commercial edition, no paywalled features |

## Tech stack

JDK 21 · Spring Boot 4.1 · Spring Cloud 2025.1 · Nacos · Apache Dubbo 3 · Sa-Token · MyBatis-Plus · Redis / Redisson · Warm-Flow · SnailJob

## Quick start

**Requirements:** JDK 21, Maven 3.9, MySQL 8 or PostgreSQL (set `TUOYU_DB_TYPE=postgresql`), Redis 6+, Nacos 3

```bash
mvn clean verify
```

Database schemas are migrated automatically on startup (Flyway; create the empty databases first), or you can initialize them by hand with the scripts in `deploy/sql`. Nacos configuration is in `deploy/config/nacos`. Full deployment docs are being written.

## Related repositories

- Frontend: [tuoyu-cloud-frontend](https://gitee.com/tuoyucode/tuoyu-cloud-frontend)

## License

[MIT License](LICENSE). See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for third-party code and files included in this project.
