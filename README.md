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
- **EmbeddingProvider**: Strategy-based routing with BM25 fallback when embedding service is unavailable
- **Partial Success**: Returns `partial_success` status with degraded warning when embedding call fails (BM25-only results)
- **Contract-First Design**: API defined via OpenAPI spec (podcast-spec submodule)

## Tech Stack

| Category | Technology |
|----------|------------|
| Framework | Spring Boot 4.0.1 |
| Language | Java 21 LTS |
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

- Java 21+
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

### 5. Populate Elasticsearch

The backend requires podcast data indexed into Elasticsearch. Use **podcast-search** to ingest:

```bash
cd ../podcast-search

# Step 1: generate embeddings (local machine)
python -m src.pipelines.embed_episodes --batch-size 256

# Step 2: ingest into ES
# Local ES (default chunk size 500 is fine):
ES_ENV=local ES_HOST=http://localhost:9200 \
  python -m src.pipelines.embed_and_ingest --from-cache

# Remote / production ES (reduce chunk size to avoid request timeouts):
ES_ENV=prod ES_HOST=https://your-es-host \
  ES_API_KEY_ID=... ES_API_KEY_SECRET=... \
  python -m src.pipelines.embed_and_ingest \
  --from-cache --batch-size 64 --es-chunk-size 100 \
  --show-ids <show_id_1> <show_id_2> ...
```

Key parameters:
- `--batch-size`: embedding model batch size (memory usage); unrelated to ES
- `--es-chunk-size`: documents per ES bulk HTTP request (default 500; use 100–200 for remote)

For segmented remote ingestion, run the above command multiple times with different `--show-ids` subsets (for example, by show or by date range) to keep each run small and resumable.

### 6. Verify Setup

```bash
# Health check
curl http://localhost:8080/health

# Search episodes
curl -X POST http://localhost:8080/api/search/episodes \
  -H "Content-Type: application/json" \
  -d '{"q": "technology", "page": 1, "size": 10}'
```

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
| `EPISODES_ALIAS_ZH_TW` | ES alias for Traditional Chinese episodes | `episodes-zh-tw` |
| `EPISODES_ALIAS_ZH_CN` | ES alias for Simplified Chinese episodes | `episodes-zh-cn` |
| `EPISODES_ALIAS_EN` | ES alias for English episodes | `episodes-en` |
| `EMBEDDING_API_URL` | External embedding API URL (OpenAI-compatible) | — |
| `EMBEDDING_API_KEY` | External embedding API key | — |
| `EMBEDDING_MODEL_ZH` | Chinese embedding model | `paraphrase-multilingual-MiniLM-L12-v2` |
| `EMBEDDING_MODEL_EN` | English embedding model | `paraphrase-multilingual-MiniLM-L12-v2` |
| `EMBEDDING_TIMEOUT_MS` | Embedding HTTP timeout (ms) | `2000` |

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
| `page` | int | `1` | Page number |
| `size` | int | `20` | Results per page (max 50) |
| `lang` | string | — | Language routing: `zh-tw`, `zh-cn`, `en`, `zh-both` |
| `mode` | string | `bm25` | Search mode: `bm25`, `knn`, `hybrid`, `exact` |
| `sort` | string | — | Sort order: `relevance` (default) or `date` |

**Search Modes:**

| Mode | Description | Use Case |
|------|-------------|----------|
| `bm25` | Text matching (TF-IDF) | Keyword search |
| `knn` | Semantic search (embedding) | Concept search |
| `hybrid` | BM25 + kNN + RRF fusion | **Recommended** |
| `exact` | Exact phrase match | Precise search |

**Example:**
```bash
# Hybrid search, Traditional Chinese only
GET /api/search/episodes?q=AI&mode=hybrid&lang=zh-tw

# Exact phrase match, any language
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