# Environment Profiles

LabWatch now separates runtime behavior with Spring profiles instead of relying on one mutable config shape.

## Profiles

- `local`: developer-friendly defaults, SQL logging on, auth off by default
- `demo`: recruiter/demo-safe defaults, auth off, mock AI provider, quieter logs
- `staging`: migration-driven startup, auth on by default, OpenAI provider expected by default
- `prod`: same safety posture as staging with production-oriented defaults

## Compose default

`docker compose` now defaults backend services to:

```bash
LABWATCH_SPRING_PROFILE=demo
```

That preserves the easy local demo flow while still using Flyway migrations and `ddl-auto=validate`.

## Override examples

Run a local developer stack:

```bash
LABWATCH_SPRING_PROFILE=local docker compose up --build
```

Run a staging-like local stack:

```bash
LABWATCH_SPRING_PROFILE=staging LABWATCH_AUTH_ENABLED=true docker compose up --build
```

## Migration behavior

- Flyway is enabled in the persistence-owning services.
- Each service uses its own Flyway history table because they share one Postgres database.
- Existing non-empty databases can transition with `baseline-on-migrate=true`.
- Fresh databases are created from versioned SQL migrations.

## JPA safety

Deployment-oriented profiles rely on:

```yaml
spring.jpa.hibernate.ddl-auto=validate
```

If you truly need schema experimentation during local development, override `SPRING_JPA_HIBERNATE_DDL_AUTO` manually, but the default path is now migration-driven.
