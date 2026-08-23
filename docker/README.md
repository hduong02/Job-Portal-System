# Local email preview

The notification service sends application status emails to MailHog. MailHog captures messages locally and does not deliver them to recipients or apply a provider sending quota.

- Full stack: `docker compose --env-file docker/.env -f docker/docker-compose.yml up -d`
- Local Java services: `docker compose -f docker/docker-compose.dev.yml up -d mailhog`
- Inbox: http://localhost:8025
- SMTP: `localhost:1025` for a service running on your machine, or `mailhog:1025` for the notification service in the full Compose stack.

The notification service uses `no-reply@job-portal.local` as its sender. Set `MAIL_FROM` to change the displayed sender address. The full Compose stack supplies SMTP settings to the notification container explicitly, so it also works when an external config server has different mail defaults.
