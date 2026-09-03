# Local email preview

The notification service sends application status emails to MailHog. MailHog captures messages locally and does not deliver them to recipients or apply a provider sending quota.

- Full stack (published images): `docker compose --env-file docker/.env -f docker/docker-compose.yml up -d`
- Full stack (locally built images): follow the [root quick start](../README.md#quick-start-docker-compose), which also includes `-f docker/docker-compose.local.yml` to select the locally built gateway.
- Local Java services: `docker compose -f docker/docker-compose.dev.yml up -d mailhog`
- Inbox: http://localhost:8025
- SMTP: `localhost:1025` for a service running on your machine, or `mailhog:1025` for the notification service in the full Compose stack.

The notification service uses `no-reply@job-portal.local` as its sender. For local Java runs, set `MAIL_FROM` in the notification service terminal. For the full Compose stack, change `APP_MAIL_FROM` under `notification-service.environment` in `docker-compose.yml`, or supply it through a Compose override, then rerun the stack's `up -d` command. Setting `MAIL_FROM` in `docker/.env` does not change the container sender because Compose supplies `APP_MAIL_FROM` explicitly.

The full Compose stack also supplies SMTP settings to the notification container explicitly, so it works when an external config server has different mail defaults.
