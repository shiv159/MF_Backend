# ⚙️ PlanMyFunds - Backend API

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-F2F4F9?style=for-the-badge&logo=spring-boot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://java.com/)
[![Google Cloud](https://img.shields.io/badge/Google_Cloud-4285F4?style=for-the-badge&logo=google-cloud&logoColor=white)](https://cloud.google.com/)

This is the central REST API and WebSocket backend for **PlanMyFunds**, built with Java and Spring Boot. It handles user authentication, mutual fund data serving, portfolio diagnostics, and integrates with OpenAI via Spring AI to power the frontend's intelligent chat assistant.

👉 **[Frontend Repository (Angular)](https://github.com/shiv159/MF_FRONTEND)**
👉 **[Live Application](https://reviewmymf.netlify.app/)**

---

## 🎯 Key Features

* **🔐 Robust Security:** Stateless JWT-based authentication combined with Google OAuth2 integration.
* **🧠 Spring AI Integration:** Context-aware endpoint (`/api/chat/message`) that processes portfolio data and streams Markdown-formatted guidance back to the user.
* **🔌 WebSocket/STOMP Support:** Real-time, bidirectional communication configured via SockJS for the AI assistant and live updates.
* **📊 Portfolio Analytics Engine:** Custom endpoints to calculate risk profiles, detect fund overlaps, and process manual portfolio selections.
* **☁️ Cloud-Ready:** Containerized and optimized for deployment on Google Cloud Run.

---

## 💻 Tech Stack

| Category | Technology |
| :--- | :--- |
| **Core Framework** | Java 17+ / Spring Boot 3.x |
| **Security** | Spring Security (JWT + OAuth2 Resource Server) |
| **AI Integration** | Spring AI (OpenAI API) |
| **Real-Time Comm.** | Spring WebSocket + STOMP / SockJS |
| **Build Tool** | Maven (or Gradle) |
| **Deployment** | Google Cloud Run, Docker |

---

## 🚀 Getting Started

### Prerequisites

* **Java Development Kit (JDK):** Version 17 or higher
* **Maven:** Version 3.8+ 
* **Database:** (e.g., PostgreSQL/MySQL - *Update this based on your actual DB*)
* **OpenAI API Key:** Required for the chat assistant functionality.

### Installation & Local Setup

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/shiv159/MF_Backend.git](https://github.com/shiv159/MF_Backend.git)
    cd MF_Backend
    ```

2.  **Configure Environment Variables:**
    Create an `application-dev.yml` or `.env` file in your `src/main/resources` directory and add your secrets:
    ```properties
    SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/planmyfunds
    SPRING_DATASOURCE_USERNAME=your_db_user
    SPRING_DATASOURCE_PASSWORD=your_db_password
    
    JWT_SECRET=your_super_secret_jwt_key_here
    SPRING_AI_OPENAI_API_KEY=sk-your-openai-api-key
    GOOGLE_CLIENT_ID=your_google_oauth_client_id
    GOOGLE_CLIENT_SECRET=your_google_oauth_client_secret
    ```

3.  **Build and Run:**
    ```bash
    mvn clean install
    mvn spring-boot:run
    ```
    The API will start on `http://localhost:8080`.

---

## 📡 Core API Endpoints

The frontend relies on the following key endpoints. All protected routes require a valid JWT passed in the `Authorization: Bearer <token>` header.

### Authentication
* `POST /api/v1/auth/login` - Standard email/password login
* `POST /api/v1/auth/register` - Create a new user account
* `GET /api/v1/auth/me` - Fetch current authenticated user details
* `GET /oauth2/authorization/google` - Initiate Google SSO flow

### Portfolio & Planning
* `POST /api/onboarding/risk-profile` - Submit risk wizard answers and generate profile
* `GET /api/funds?query={text}&limit=20` - Search available mutual funds
* `POST /api/portfolio/manual-selection` - Submit selected funds for diagnostic analysis

### AI Chat & Real-time
* `POST /api/chat/message` - Send a prompt to the Spring AI assistant
* `GET/CONNECT /ws` - SockJS/STOMP endpoint for WebSocket connections

---

## 📂 Project Architecture

```text
src/
 └── main/
      ├── java/com/planmyfunds/
      │    ├── config/           # Security, CORS, and WebSocket configs
      │    ├── controllers/      # REST API endpoints
      │    ├── services/         # Business logic & Spring AI integration
      │    ├── models/           # JPA Entities and DTOs
      │    ├── repositories/     # Spring Data JPA interfaces
      │    └── security/         # JWT filters, OAuth2 success handlers
      └── resources/
           ├── application.yml   # Core Spring Boot configuration
           └── prompts/          # System prompts for Spring AI
