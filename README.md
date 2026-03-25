# podcast-backend

Podcast search API service built with Spring Boot 4 and Elasticsearch 8, providing **Hybrid Search** (BM25 + kNN + RRF) for 175K+ podcast episodes with rate limiting and circuit breaker patterns.

## Architecture Overview

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│podcast-frontend │────▶│ podcast-backend │────▶│ Elasticsearch   │
│   (Next.js)     │     │  (Spring Boot)  │     │     8.11        │
└─────────────────┘     └────────┬────────┘     └─────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
              ┌─────▼─────┐           ┌───────▼───────┐
              │  Search   │           │   Rankings    │
              │  Service  │           │   Service     │
              │(ES Query) │           │ (Apple API)   │
              └───────────┘           └───────────────┘
```

## Features

- **Hybrid Search**: BM25 + kNN + RRF (Reciprocal Rank Fusion) for best-of-both-worlds search
- **Multiple Search Modes**: `bm25`, `knn`, `hybrid`, `exact` (match_phrase)
- **Time Decay**: Boost recent content with configurable decay parameters
- **Multi-Language Support**: Chinese (IK Analyzer) and English with cross-language search
- **Mustache Query Templates**: Flexible Elasticsearch query generation with templating
- **Apple Charts Rankings**: Cached podcast rankings by region (Taiwan, US, China)
- **Rate Limiting**: Configurable request limits per endpoint (Resilience4j)
- **Circuit Breaker**: Graceful degradation for external API failures
- **Contract-First Design**: API defined via OpenAPI spec (podcast-spec submodule)

## Tech Stack

| Category | Technology |
|----------|------------|
| Framework | Spring Boot 4.0.1 |
| Language | Java 17 |
| Search Engine | Elasticsearch 8.11.1 |
| Resilience | Resilience4j 2.2.0 |
| API Docs | SpringDoc OpenAPI 2.8.4 |
| Templating | Mustache 0.9.10 |
| Build | Maven 3.9 |

## Project Structure

```
podcast-backend/
├── src/
│   ├── main/java/com/example/podcastbackend/
│   │   ├── config/              # Elasticsearch, CORS, Rate limiting
│   │   ├── controller/          # REST API endpoints
│   │   ├── service/             # Business logic
│   │   ├── search/
│   │   │   ├── client/          # Elasticsearch client wrapper
│   │   │   ├── mapper/          # ES response mappers
│   │   │   └── query/           # Mustache query builders
│   │   ├── request/             # API request DTOs
│   │   ├── response/            # API response DTOs
│   │   ├── exception/           # Global exception handling
│   │   └── cache/               # Rankings cache
│   └── resources/
│       ├── application.yml      # Configuration
│       └── podcast-spec/        # ES query templates
├── Dockerfile                   # Multi-stage build
├── pom.xml
└── mvnw
```

## Quick Start

### 1. Prerequisites

- Java 17+
- Elasticsearch 8.11+ running on `localhost:9200`

### 2. Clone and Build

```bash
git clone --recurse-submodules <repo-url>
cd podcast-backend
./mvnw clean install -DskipTests
```

### 3. Configure Environment

Create `.env` or set environment variables:

```bash
ELASTICSEARCH_HOST=localhost
ELASTICSEARCH_PORT=9200
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

### 4. Start the Server

```bash
./mvnw spring-boot:run
```

The API will be available at http://localhost:8080

### 5. Verify Setup

```bash
# Health check
curl http://localhost:8080/health

# Search episodes
curl -X POST http://localhost:8080/api/search/episodes \
  -H "Content-Type: application/json" \
  -d '{"q": "technology", "page": 1, "size": 10}'
```

> **Note**: Requires Elasticsearch with indexed podcast data.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ELASTICSEARCH_HOST` | Elasticsearch host | `localhost` |
| `ELASTICSEARCH_PORT` | Elasticsearch port | `9200` |
| `ELASTICSEARCH_SCHEME` | Connection scheme | `http` |
| `ELASTICSEARCH_INDEX_SHOWS` | Shows index name | `shows` |
| `ELASTICSEARCH_INDEX_EPISODES` | Episodes index name | `episodes` |
| `CORS_ALLOWED_ORIGINS` | Allowed CORS origins | `http://localhost:3000` |
| `RANKINGS_CACHE_TTL` | Rankings cache TTL (seconds) | `3600` |

## API Endpoints

### Search

| Method | Endpoint | Description | Rate Limit |
|--------|----------|-------------|------------|
| GET | `/api/search/shows` | Search podcasts | 50/sec |
| GET | `/api/search/episodes` | Search episodes | 50/sec |

**Episode Search Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `q` | string | required | Search query |
| `page` | int | 1 | Page number |
| `size` | int | 10 | Results per page |
| `mode` | string | `bm25` | Search mode: `bm25`, `knn`, `hybrid`, `exact` |
| `timeDecay` | boolean | true | Enable time decay for recent content |
| `timeDecayScale` | string | `90d` | Decay scale (e.g., `30d`, `60d`) |
| `timeDecayRate` | double | 0.5 | Decay rate (0-1) |

**Search Modes:**

| Mode | Description | Use Case |
|------|-------------|----------|
| `bm25` | Text matching (TF-IDF) | Keyword search |
| `knn` | Semantic search (embedding) | Concept search |
| `hybrid` | BM25 + kNN + RRF fusion | **Recommended** |
| `exact` | Exact phrase match | Precise search |

**Example:**
```bash
# Hybrid search with time decay
GET /api/search/episodes?q=AI&mode=hybrid&timeDecay=true

# Exact phrase match
GET /api/search/episodes?q=machine+learning&mode=exact
```

### Rankings

| Method | Endpoint | Description | Rate Limit |
|--------|----------|-------------|------------|
| GET | `/api/rankings` | Apple Charts rankings | 30/sec |

**Query Parameters:**
- `region`: `tw`, `us`, or `cn` (default: `tw`)
- `type`: `podcast` or `episode` (default: `podcast`)
- `limit`: 1-100 (default: `20`)

### Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check |
| GET | `/actuator/health` | Actuator health |

## API Documentation

Swagger UI available at: http://localhost:8080/swagger-ui.html

OpenAPI spec: http://localhost:8080/v3/api-docs

## Available Commands

| Command | Description |
|---------|-------------|
| `./mvnw spring-boot:run` | Start development server |
| `./mvnw clean install` | Build the project |
| `./mvnw test` | Run tests |
| `./mvnw package -DskipTests` | Build JAR without tests |

## Testing

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=SearchServiceTest

# Run with coverage
./mvnw test jacoco:report
```

## Docker

Build and run with Docker:

```bash
# Build image
docker build -t podcast-backend .

# Run container
docker run -p 8080:8080 \
  -e ELASTICSEARCH_HOST=host.docker.internal \
  -e CORS_ALLOWED_ORIGINS=http://localhost:3000 \
  podcast-backend
```

### Docker Compose (Development)

```bash
cd ../podcast-infra/docker
docker-compose -f docker-compose.dev.yml up
```

This starts Elasticsearch, Kibana, Backend, and Frontend together.

## Related Projects

- **podcast-frontend**: Search UI (Next.js)
- **podcast-search**: RSS parsing, data cleaning, ES indexing (Python)
- **podcast-crawler**: RSS fetching, show metadata extraction (Python)
- **podcast-spec**: OpenAPI specification (Git submodule)
- **podcast-infra**: Infrastructure and deployment