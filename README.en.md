[中文](README.md) | [English](README.en.md)

# SignageHub · Digital signage and offline player

ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.) · https://www.zhuatech.cn/

Publish image, short-video and text programs to store, showroom or reception screens. The CMS manages media, groups, drafts and limited schedules. The computer player verifies a complete program before activating it and keeps using an authorized disk cache while the CMS is unavailable.

**Public source learning edition / non-commercial source.** Personal study and non-commercial exchange only; commercial use requires written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. See [LICENSE](LICENSE). Intended for small screen networks and implementation research. External adoption, payments and production capacity have not been validated.

## The complete content path

|Interface|Implemented behavior|
|---|---|
|Content workspace|PNG/JPEG and H.264/AAC MP4 upload, format/dimension/quota checks, preview and availability; ordered media/text drafts, bilingual names, categories, portrait/landscape canvas, fit and colors|
|Publication and devices|Organization-scoped screens/groups, separate device tokens, baseline publication, limited schedules, overlap rejection, cancellation, immutable releases and baseline rollback; target and actual playback tracked separately|
|Computer player|Local Node service and browser renderer, SHA-256 checks, private disk cache, complete activation, offline schedules/leases, process restart recovery, crash supervisor and restricted local setup|
|Administration|BCrypt 12, sessions/CSRF, login limits, password change and session revocation; accounts/roles/permissions, organizations, menus, dictionaries, parameters, audit, search/filter/pagination and screen-status CSV|

A publication freezes its contents and selected screens. Editing a draft cannot modify a published release. A stable request ID recovers the same publication after a lost response; stale draft versions are rejected. A limited schedule takes priority during its UTC interval and then returns to the baseline, including when the CMS is offline.

The player receives the baseline and schedules for the next seven days. All assets must pass verification before activation. The offline lease is configurable from 1 to 72 hours. Expiration, corrupt cache or a significant clock rollback stops playback. Disabling a screen or rotating a token revokes it after reconnection; an offline device can continue its already-authorized cache until the lease expires. See [business rules](docs/BUSINESS.md) and [operations](docs/OPERATIONS.md).

## Running screens

These screenshots show the running application. QA-marked content belongs to an isolated validation environment, is not installed as business seed data and is not a customer example. Device tokens, passwords and customer information are excluded. The test environment uses a separate port; Compose defaults to port 8170 and supports a configurable port.

![Sign-in](docs/screenshots/login.jpg)
![Verified media library](docs/screenshots/media.jpg)
![Ordered program editor](docs/screenshots/program.jpg)
![Program items: text, image order and display duration](docs/screenshots/program-items.jpg)
![Screens and groups](docs/screenshots/screens.jpg)
![Publication and schedule](docs/screenshots/publication.jpg)
![Target and reported playback](docs/screenshots/overview.jpg)
![Roles and permissions](docs/screenshots/roles.jpg)
![Private local player settings](docs/screenshots/player-settings.jpg)
![Actual cached playback](docs/screenshots/player.jpg)
![Actual H.264 playback with a validation test pattern](docs/screenshots/player-video.jpg)
![Narrow-screen workspace](docs/screenshots/mobile.jpg)
![English interface](docs/screenshots/english.jpg)

## Architecture, directory and requirements

Browser → same-origin Nginx API → Spring Security and organization scopes → transactional business services → MySQL. Media has a separate persistent volume. Device credentials are separate from staff sessions; the CMS stores only token hashes, and the device retains its token in a private local configuration file.

Java 21, Spring Boot 4.0.7, Security/JPA/Flyway, MariaDB Connector/J 3.5.10 with MySQL 8.4; Vue 3.5.43, Vite 8.1.5, Node 24.19.0 and Nginx 1.29. The backend invokes distribution-provided ffprobe for video metadata validation. Third-party licenses remain separate.

```text
backend/                Identity, media, programs, publication, schedules and device APIs
  src/main/resources/   Configuration and Flyway V1/V2 migrations
frontend/               Content, device, publication and admin interfaces
  public/brand/         Official brand assets
player/                 Node disk cache, local setup/renderer and supervisor
scripts/                Private initialization, QA, persistence, backup/restore and service-file generation
docs/                   Operations, API, security, deployment, tests, licenses and screenshots
compose.yaml            Persistent MySQL/media, backend and frontend
compose.test.yaml       Separate temporary MySQL tests; never merge into production
```

Server: Docker/Compose v2 and Python 3.10+, with at least 4 GB free memory recommended. Source debugging additionally needs JDK 21, Maven 3.9 and Node ≥24.19. The computer player needs Node ≥24.19, a modern browser and enough disk for its cache. Combined media assigned to one screen is limited to 256 MB.

## Install and publish your first program

```sh
git clone https://github.com/zhuatech-han/zhuatech-signagehub.git
cd zhuatech-signagehub
python3 scripts/init-env.py
docker compose -p signagehub config --quiet
docker compose -p signagehub up --build -d --wait --wait-timeout 3600
python3 scripts/readiness.py
```

Open the [local CMS](http://127.0.0.1:8170/) and [health endpoint](http://127.0.0.1:8170/actuator/health). Default username: `admin`. Its unique strong password is in private `.env` as `ADMIN_PASSWORD`; no shared password is provided. Initialization creates mode 0600 configuration and refuses to overwrite it. Restart does not reset existing accounts.

Upload media → save a program → create a screen and retain its one-time token → publish → connect the computer player. An empty database seeds identity, roles, permissions, organization, menus, categories and parameters only, without fabricated stores/screens/content.

```sh
cd player
node src/launcher.js
```

On the playback computer, open [local settings](http://127.0.0.1:8171/), enter an accessible HTTPS CMS origin and device token, then open [the display](http://127.0.0.1:8171/display). Same-computer development allows `http://127.0.0.1:8170`. Localhost on another computer is not the CMS server. No npm runtime packages are required by the player; npm packages support development checks only.

Local setup listens on 127.0.0.1. Tokens do not enter playback URLs, browser storage or logs. Keep private `player-data/` configuration/state files (0600) and its directory (0700) outside source control.

The CMS workspace supports Chinese and English. Local player settings are currently Chinese; program text can be edited in other languages.

## Database, configuration and upgrades

[V1 identity](backend/src/main/resources/db/migration/V1__identity.sql) → [V2 signage](backend/src/main/resources/db/migration/V2__signage.sql). Flyway migrations and JPA schema validation are enabled. Back up before upgrading; never edit an already-applied migration. READ COMMITTED plus organization write locks protects concurrent publication and stale-version updates.

|Configuration|Purpose|
|---|---|
|DATABASE_PASSWORD / MYSQL_ROOT_PASSWORD|Independent database credentials; no public database port|
|ADMIN_USERNAME / ADMIN_PASSWORD|First administrator; existing identity remains unchanged|
|BIND_ADDRESS / WEB_PORT|Default 127.0.0.1:8170; configurable|
|COOKIE_SECURE|Set true for external HTTPS deployment|
|TEST_DATABASE_PASSWORD / TEST_ROOT_PASSWORD|Isolated tests, never production credentials|
|PLAYER_DATA_DIR / PLAYER_PORT|Private player data and localhost port; defaults player-data/8171|
|PLAYER_POLL_MS|Sync interval, default 15000 ms, minimum 5000 ms|

`offlineHours` controls subsequent leases (1–72 hours). `screenLimit` caps screens per organization (1–500). Each organization has a 512 MB media quota. Files: 64 MB maximum; images up to 4096×4096; MP4 up to 1920×1920 and 300 seconds, H.264 video/AAC audio only. A program has at most 100 items. Catalogs are bounded at 10000 records; UI pagination uses 15 rows. Large-scale screen networks have not been certified.

## Tests, deployment and recovery

```sh
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ../player
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
cd ../backend
mvn -B spotless:check test
cd ..
docker volume create signagehub-maven-cache
docker compose -f compose.test.yaml -p signagehub-quality up --abort-on-container-exit --exit-code-from tests
```

Backend: 38 tests cover releases, authorization, sessions, adjacent/overlapping intervals, rollback and concurrency. Frontend: 14 tests cover requests, CSRF, unknown outcomes and out-of-order directory reads. Player: 24 tests include real localhost HTTP, checksums, leases, corrupt cache, ranges, process restart and supervisor recovery. See [test results](docs/TESTING.md) for actually executed environments. Docker Maven builds never skip tests.

Run `python3 scripts/quality.py --project signagehub --video` only against an isolated local QA deployment; it creates clearly named QA records and private output state. Verify persistence with `python3 scripts/verify-persistence.py`.

```sh
python3 scripts/backup.py --project signagehub --output /private/path/signagehub-backup.zip
python3 scripts/restore-env.py --source .env --output /private/path/restore.env --port 8172
python3 scripts/restore.py /private/path/signagehub-backup.zip --project signagehub-restore --env-file /private/path/restore.env
```

Backup pauses backend writes and captures SQL and media together. Restore accepts trusted checksummed backups only into new empty projects/volumes on a separate localhost port. ZIP/TAR paths, sizes and member types are checked; each member is limited to 1 GB. Backups exclude `.env` and player pairing files; retain required configuration separately and privately. [Deployment](docs/DEPLOYMENT.md) covers HTTPS, upgrades, service-file generation and recovery.

## Limits and troubleshooting

- CMS and computer-player source support manual startup. macOS/systemd/Windows login service-file generation is provided; actual OS login/reboot, unattended browser launch, TV power/HDMI, native Android APK, physical Windows/Linux devices and specific TV models require separate acceptance. This is not an all-device compatibility claim.
- Playback is muted. Browser H.264 decoding is required. No video transcoding, freeform multi-zone editor, embedded websites, ready-made POS/ERP connectors, remote device reboot/shutdown, ad billing, multi-tenant SaaS or production-capacity certification.
- Check private administrator initialization before troubleshooting sign-in. Existing accounts need authorized password reset. Video rejection requires checking ffprobe, codecs and dimensions.
- A target release is not proof of playback. PLAYING requires a local renderer acknowledgement. Network failures retain valid cache; expired leases need reconnection. Clock/cache/local-service errors are explicit.
- Disabling media prevents new publication but retains published snapshots. Referenced media cannot be deleted. Screens/programs with history are retained; disable screens when withdrawing them. Offline revocation follows the lease boundary above.
- Historical-release archiving and automatic storage reclamation are not implemented. Retained references count toward the 512 MB organization quota; frequently replaced videos require an extended retention policy. Upload checks metadata and codecs; decoding must still be verified on the target playback computer.
- No hosted public demo, real customer or payment case is claimed. Review suitability, backups, maintenance and [security](docs/SECURITY.md) before deployment.

## Contact, licensing and contribution

Commercial licensing, private deployment, device adaptation, implementation and system integration: [website](https://www.zhuatech.cn/), [han@zhuatech.cn](mailto:han@zhuatech.cn), [jack@zhuatech.cn](mailto:jack@zhuatech.cn), WhatsApp [+86 17521234993](https://wa.me/8617521234993).

Provided by ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.) for personal study, technical research and non-commercial exchange. Commercial use requires written authorization. Enterprise digitization, SME digital and AI transformation, private deployment, software development/implementation, FDE outsourcing, OPC support and customization inquiries are welcome through the contacts above.

Issues should contain sanitized reproduction steps, version and environment. Report vulnerabilities privately through company contacts; never publish credentials. Contributions must describe business behavior and relevant tests and preserve company attribution and third-party copyrights. [Third-party licenses](docs/THIRD_PARTY.md) apply independently. The source is provided as-is; verify security, environment compatibility and intended use before deployment.
