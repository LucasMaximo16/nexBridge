# NexBridge — CLAUDE.md

## Project Overview
NexBridge is an Intelligent Legacy Integration Fabric built with Spring Boot 3.2, Java 21, and PostgreSQL.

## Architecture
- **Domain layer**: JPA entities and repositories in `com.nkd.nexbridge.domain`
- **API layer**: DTOs and controllers in `com.nkd.nexbridge.api`
- **Exception handling**: Centralized in `com.nkd.nexbridge.exception`
- **Database migrations**: Flyway V1-V7 in `src/main/resources/db/migration`

## Key Technologies
- Spring Boot 3.2.3
- Java 21
- PostgreSQL 16
- Flyway (migrations)
- Spring Security + JWT (jjwt 0.12.3)
- Spring Kafka
- Lombok + MapStruct
- Testcontainers (tests)

## Running Locally
```bash
docker-compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Building
```bash
mvn clean package -DskipTests
```

## Database
- Schema managed by Flyway
- `audit_log` table is partitioned by year
- JSONB columns used for flexible config/fields storage

## Session History
- **Session 1**: Project scaffold, pom.xml, configurations, Flyway migrations V1-V7, JPA entities, repositories, DTOs, exception hierarchy
