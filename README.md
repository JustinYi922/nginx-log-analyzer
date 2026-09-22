# Nginx Log Analyzer

**English** | [中文](README.zh-CN.md)

Parse nginx `access.log` into MySQL and explore traffic with a local web dashboard. Useful for troubleshooting, capacity checks, and spotting unusual IPs or paths.

**Author:** [JustinYi922](https://github.com/JustinYi922/nginx-log-analyzer) · **Email:** 635344900@qq.com  
**License:** [MIT](LICENSE) · **Commercial use & attribution:** [COMMERCIAL.md](COMMERCIAL.md)  
**Repository:** https://github.com/JustinYi922/nginx-log-analyzer

## Features

### Log import

- **Browser upload**: pick a `.log` / `.txt` file; optionally truncate before import (recommended)
- **Absolute path import**: parse a large file already on the server
- **Batch-friendly**: writes to MySQL in batches (default 2000 rows); upload limit up to ~2GB
- **Progress bar**: poll import progress so large files do not look stuck
- **One-click clear**: clear the table from the UI or API to re-analyze another log

### Parsed fields

Combined-style access logs, including common upstream fields, stored for aggregation:

| Field | Typical use |
|------|-------------|
| remote_addr / x_forwarded_for | Client IP, proxy chain |
| access_time | Time filters, day/hour trends |
| method / path / protocol | Hot APIs and paths |
| status / body_bytes | Success/error share, payload size |
| referer / user_agent | Referrers and clients |
| upstream_addr / upstream_response_time | Upstream nodes and latency |

If your format differs, adjust `NginxAccessLogParser` or open an issue with a redacted sample line.

### Web dashboard

Dark single-page UI (Chart.js)—no separate frontend build:

- **Overview cards**: total requests, labeled IP share, 2xx / 4xx / 5xx rates
- **Status code** and **HTTP method** share (doughnut charts)
- **Source ownership**: rollup by configured IP labels
- **Traffic by day / hour** (line charts)
- **Top path / client IP / User-Agent / Upstream** tables
- **Filters**: time range + multiple IPs (comma/space); default last 7 days

### MySQL & ops

- Gear icon **MySQL settings**: Host / Port / Database / User / Password
- **Test connection** → **Save & switch**; stored in `~/.nginx-log-analyzer/db.json` (not in the repo)
- Can create database/tables on first connect (or create the DB yourself)
- Default port `8099` → http://127.0.0.1:8099/

### Optional IP labels

Configure CIDR / prefix / ranges in `application.yml`. The dashboard shows labeled-segment share and ownership on Top IPs (office, IDC, business nets, etc.).

### Other

- Java 8+ / Spring Boot, runnable as a single jar
- REST API for import, progress, stats, and DB config (script-friendly)
- Unit tests for parser and IP labels; GitHub Actions runs `mvn test`

## Requirements

- Java 8 or newer
- MySQL 5.7+ / 8.x
- Maven 3.6+ (only when building from source)

## Quick start

### 1. Prepare MySQL

Create an empty database (optional — the app can also create it when you apply settings in the UI):

```sql
CREATE DATABASE nginx_log DEFAULT CHARACTER SET utf8mb4;
```

### 2. Configure

Edit `src/main/resources/application.yml`, or start with defaults and set the connection on the web page (gear button).

### 3. Run from source

```bash
mvn spring-boot:run
```

Open http://127.0.0.1:8099/

### 4. Or run the packaged jar

```bash
mvn -DskipTests package
java -jar target/nginx-log-analyzer-0.1.0-SNAPSHOT.jar
```

## Import logs

**Upload (recommended):** choose a file on the page → optionally truncate → **Upload & analyze**.

**By absolute path:**

```bash
curl -X POST http://127.0.0.1:8099/api/import \
  -H 'Content-Type: application/json' \
  -d '{"filePath":"/path/to/access.log","truncate":true}'
```

**Clear all rows:**

```bash
curl -X POST http://127.0.0.1:8099/api/clear
```

Import progress: `GET /api/import/progress`

## Supported log format

Combined-style nginx access log with common extra fields, for example:

```text
$remote_addr - $remote_user [$time_local] "$request" $status $body_bytes_sent
"$http_referer" "$http_user_agent" "$http_x_forwarded_for"
"$upstream_addr" $upstream_response_time
```

If your format differs, adjust `NginxAccessLogParser` or open an issue with a sample line (redact secrets).

## IP labels (optional)

In `application.yml`:

```yaml
nginx-log:
  ip-labels:
    - label: office
      ranges:
        - 10.0.0.0-10.0.0.255
        - 192.168.1.
```

Supported range styles: exact IP, prefix (`10.0.0.`), interval (`a.b.c.d-a.b.c.e`).

## API overview

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/db/config` | Current DB settings (password masked) |
| POST | `/api/db/test` | Test connection |
| POST | `/api/db/apply` | Save & switch datasource |
| POST | `/api/import` | Import by file path |
| POST | `/api/import/upload` | Multipart upload import |
| GET | `/api/import/progress` | Import progress |
| POST | `/api/clear` | Truncate table |
| GET | `/api/stats` | Aggregated stats (time range, IPs, limit) |
| GET | `/api/stats/overview` | Overview card metrics |

## Project layout

```text
src/main/java/com/gwamcc/nginxlog/   Java sources
src/main/resources/static/           Dashboard (index.html)
src/main/resources/db/schema.sql     Table DDL
src/test/java/                       Unit tests
.github/workflows/ci.yml             CI
COMMERCIAL.md                        Commercial use & attribution
README.zh-CN.md                      Chinese README
```

## License & commercial use

- License: [LICENSE](LICENSE) (MIT)
- Commercial attribution & notification: [COMMERCIAL.md](COMMERCIAL.md), [NOTICE](NOTICE)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Issues and pull requests are welcome.

## Author & contact

- GitHub: https://github.com/JustinYi922/nginx-log-analyzer
- Email: 635344900@qq.com

## Disclaimer

Use this tool only on systems and logs you are authorized to inspect. Do not commit real production logs or database credentials to a public repository.
