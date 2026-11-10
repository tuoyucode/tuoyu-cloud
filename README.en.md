# TuoYu-Cloud

> A production-ready, open-source Java microservice framework: secure by default, verifiable quality, monolith and microservices from one codebase, cloud native, fully open source.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![JDK](https://img.shields.io/badge/JDK-21-green.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-blue.svg)
![Status](https://img.shields.io/badge/status-preview-orange.svg)

TuoYu-Cloud is a general-purpose framework for enterprise back-office systems. It ships with ready-to-use building blocks such as permissions, organizations, workflow, code generation, object storage, and message push. **The same codebase can be deployed as a monolith or as microservices**, so teams can evolve smoothly as their business grows.

> ⚠️ **Preview (1.0.0-alpha).** APIs and directory layout may still change; not recommended for production yet.

**User manual:** [https://docs.ctoolhub.com](https://docs.ctoolhub.com)

## Goals

| Goal | Description |
|---|---|
| **Secure by default** | No plaintext secrets in the repository; in production, startup self-checks refuse to boot with weak passwords, default keys, or exposed sensitive endpoints |
| **Monolith and microservices in one** | In-process calls in monolith mode, Dubbo in microservice mode, with no changes to business code |
| **Pluggable multi-tenancy** | Off by default with zero intrusion; when enabled, it covers the database, cache, sessions, async and scheduled jobs, and cross-service calls |
| **Verifiable quality** | CI always runs the tests (no skipping), architecture rules are checked automatically, both deployment modes have end-to-end tests, and database changes are managed with Flyway |
| **Cloud native** | Containerized deployment, health checks, a trace ID across the gateway and all services, graceful shutdown |
| **Fully open source** | MIT licensed, no commercial edition, no paywalled features |

## Tech stack

JDK 21 · Spring Boot 4.1 · Spring Cloud 2025.1 · Nacos 2.x · Apache Dubbo 3 · Sa-Token · MyBatis-Plus · Redis / Redisson · Warm-Flow · SnailJob · Flyway · MySQL 8 / PostgreSQL

## Quick start

**Requirements:** JDK 21, Maven 3.9, MySQL 8 or PostgreSQL (set `TUOYU_DB_TYPE=postgresql`), Redis 6+; the microservice mode also needs Nacos 2.x (verified with 2.5), the monolith does not

```bash
mvn clean verify
```

Database schemas are migrated automatically on startup (Flyway; create the empty databases first), or you can initialize them by hand with the scripts in `deploy/sql`. Nacos configuration is in `deploy/config/nacos`.

- **Monolith:** run `tuoyu-boot/tuoyu-boot-all` (port 8081). It needs no Nacos and reads the files in `deploy/config/nacos` directly.
- **Microservices:** import the Nacos configuration (`java tools/nacos/ImportNacosConfig.java --apply`), then start tuoyu-system, tuoyu-auth, tuoyu-resource and tuoyu-gateway (port 8081).
- **Dev environment:** `./dev help` (`.\dev.ps1 help` on Windows) lists the commands for generating `.env`, preparing MySQL, Redis and Nacos, and running the monolith.
- **docker compose:** core, full and monolith stacks plus optional add-ons such as Nginx; see [deploy/docker/README.md](deploy/docker/README.md) (in Chinese).

No secrets are stored in the repository. Generate a local `.env` with `java tools/secrets/GenerateEnv.java`; see [.env.example](.env.example) for all variables. On startup every service runs self-checks and, in production, refuses to start with weak passwords, published keys or unsafe settings.

## Related repositories

- Frontend: [tuoyu-cloud-frontend](https://gitee.com/tuoyucode/tuoyu-cloud-frontend)

## License

[MIT License](LICENSE).

The following unmodified third-party components use other licenses:

| Component | License | Location and purpose |
|---|---|---|
| [ip2region](https://github.com/lionsoul2014/ip2region) offline IP database | Apache-2.0 | `ip2region_v4.xdb` in `tuoyu-common-web`, distributed with the repository |
| [RabbitMQ Delayed Message Plugin](https://github.com/rabbitmq/rabbitmq-delayed-message-exchange) | MPL-2.0 | `deploy/docker/rabbitmq`, distributed with the repository, optional |
| [aviator](https://github.com/killme2008/aviator) | LGPL | Condition expression engine of the SnailJob server; packaged into executable jars and images as a dependency and replaceable under the LGPL |
| [simple-http](https://github.com/xkcoding/simple-http) | LGPL-3.0 | HTTP layer of third-party login; packaged into executable jars and images as a dependency and replaceable under the LGPL |

A few source files are derived from other open-source projects (Apache-2.0) and keep their original copyright and license headers. Other Maven dependencies are licensed as stated in their own distributions.
