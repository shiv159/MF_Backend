# PlanMyFunds Backend

Spring Boot backend for PlanMyFunds. It provides authentication, portfolio diagnostics, fund analytics, ETL integration, and an AI copilot workflow powered by Spring AI and LangChain4j.

## Key Features

- JWT + OAuth2 authentication
- Portfolio diagnostics and analytics APIs
- AI copilot with route selection and tool-grounded responses
- Streaming chat over Server-Sent Events (SSE)
- External ETL integration for holdings enrichment

## Tech Stack

- Java 21
- Spring Boot 3.x
- Spring Security
- Spring AI
- LangChain4j
- PostgreSQL
- Maven

## Running Locally

1. Configure environment variables and `application.properties`.
2. Run:

```bash
mvn clean install
mvn spring-boot:run
```

Default URL: `http://localhost:8080`

## Core API Endpoints

### Authentication

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `GET /api/v1/auth/me`
- `GET /oauth2/authorization/google`

### Portfolio

- `POST /api/onboarding/risk-profile`
- `GET /api/funds?query={text}&limit=20`
- `POST /api/portfolio/manual-selection`
- `GET /api/v1/portfolio/diagnostic`

### AI Chat

- `POST /api/chat/stream`
  - SSE event types: `status`, `tool_start`, `tool_result`, `message_delta`, `message_complete`, `error`

## Correlation IDs

The backend accepts and returns `X-Correlation-ID` on API requests and propagates it to downstream ETL calls for traceability.
