# Backend — CampusToursLive.ai

Spring Boot REST API serving the CampusToursLive.ai platform.

## Tech Stack

- **Java 17**
- **Spring Boot 3.4.7**
- **Spring Web** — REST controllers
- **Spring Data JPA** — data persistence layer (datasource disabled by default)
- **Spring GraphQL** — GraphQL support (dependency commented out; uncomment in `pom.xml` to enable)
- **Lombok** — boilerplate code generation
- **Maven** — build tool

## Package Structure

```
src/main/java/com/CampusToursLive/ai/bankend/
├── BackendApplication.java         # Entry point
├── config/
│   └── WebConfig.java              # ForwardedHeaderFilter for proxy-aware IP resolution
├── controller/
│   ├── UserController.java         # User management REST endpoints
│   └── FacebookLiveController.java # Facebook Live stream API endpoints
├── dto/
│   └── RequestInfo.java            # DTO carrying client request metadata
├── model/
│   └── User.java                   # JPA entity (name, age, email, password)
├── repository/
│   └── UserRepository.java         # Spring Data JPA repository
└── service/
    ├── UserService.java             # User business logic
    └── RequestInfoService.java      # Client metadata extraction
```

## Features & API Endpoints

### User Management — `/campusLive`

| Method | Path                       | Description                          |
| ------ | -------------------------- | ------------------------------------ |
| `GET`  | `/campusLive/user/{email}` | Retrieve a user by email             |
| `POST` | `/campusLive/user`         | Create or update a user              |
| `POST` | `/campusLive/login`        | Authenticate with email and password |

**User model fields:** `name`, `age`, `email` (primary key), `password`

### Facebook Live Streaming — `/api/facebook`

| Method | Path                        | Description                           |
| ------ | --------------------------- | ------------------------------------- |
| `POST` | `/api/facebook/create-live` | Create a Facebook Live stream session |

**Request parameters:** `accessToken`, `pageId`

**Response:** `videoId`, `streamUrl` (RTMP), `secureStreamUrl` (RTMPS)

Calls the Facebook Graph API to generate a live video and returns the ingest stream URLs so a guide can broadcast a campus tour to Facebook Live.

### Request Analytics — `RequestInfoService`

Extracts rich metadata from every incoming HTTP request:

- **IP address** — checks `X-Forwarded-For`, `Forwarded`, `X-Real-IP`, `CF-Connecting-IP`, `True-Client-IP` headers (proxy/CDN aware)
- **Browser & OS detection** — parses `User-Agent` and `Sec-CH-UA` headers
- **Bot detection** — distinguishes browser requests from automated bots
- **Full header dump** — collects all request headers for analytics

## Configuration

`src/main/resources/application.properties`:

```properties
spring.application.name=bankend

# H2 in-memory database (active by default — data resets on restart)
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.h2.console.enabled=true
```

To switch to a persistent database, replace the H2 properties above with:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/campustours
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
```

## Running Locally

### Prerequisites

- **Java 17** — [Download](https://adoptium.net/)
- **Maven** — bundled via `./mvnw` (no separate install needed)

---

### Terminal

```bash
# From the backend/ directory

# First run (downloads dependencies ~3–5 min)
./mvnw spring-boot:run

# Build a runnable JAR
./mvnw clean package
java -jar target/bankend-0.0.1-SNAPSHOT.jar
```

Server starts on **http://localhost:8080**.

---

### IntelliJ IDEA

1. **Open project** — File → Open → select the `backend/` folder. IntelliJ auto-detects `pom.xml` and imports Maven dependencies.
2. **Wait for indexing** to finish (progress bar in the bottom toolbar).
3. **Run the app** — open `src/main/java/com/CampusToursLive/ai/bankend/BackendApplication.java`, click the green ▶ button in the gutter next to the `main()` method, and select **Run**.
4. Server starts on **http://localhost:8080**.

> **Tip:** You can also use the **Run toolbar** at the top → select `BackendApplication` from the dropdown → click ▶.

---

### VS Code

1. **Install extensions:**
   - [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) (Microsoft)
   - [Spring Boot Extension Pack](https://marketplace.visualstudio.com/items?itemName=vmware.vscode-boot-dev-pack) (VMware)

2. **Open folder** — File → Open Folder → select the `backend/` folder.

3. **Wait** for the Java extension to finish importing the Maven project (notification in the bottom-right).

4. **Run the app** — two options:
   - Open `BackendApplication.java` and click **Run** above the `main()` method.
   - Or open the **Spring Boot Dashboard** panel in the sidebar → click ▶ next to `bankend`.

5. Server starts on **http://localhost:8080**.

---

### H2 Console (in-memory DB UI)

While the server is running, visit **http://localhost:8080/h2-console**.

- **JDBC URL:** `jdbc:h2:mem:testdb`
- **Username:** `sa`
- **Password:** *(leave blank)*
