# FitHub Backend API Documentation

## Quick Start

### 1. Start the Application
```bash
mvn clean install
mvn spring-boot:run -pl boot
```

Application will start on `http://localhost:8080`

### 2. Access Swagger UI
Open your browser and navigate to:
```
http://localhost:8080/swagger-ui.html
```

You'll see interactive API documentation with all endpoints, request/response schemas, and the ability to test endpoints directly.

### 3. Access OpenAPI JSON
Raw OpenAPI specification available at:
```
http://localhost:8080/v3/api-docs
```

---

## API Endpoints

### Authentication (`/auth`)

#### Register User
```http
POST /auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123",
  "name": "John Doe",
  "role": "PATIENT"  // or "PT", "NUTRITIONIST"
}
```

**Response (201):**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "name": "John Doe",
  "role": "PATIENT"
}
```

#### Login
```http
POST /auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response (200):**
```json
{
  "email": "user@example.com",
  "role": "PATIENT",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### Validate Token
```http
POST /auth/validate
Authorization: Bearer <accessToken>
```

**Response (200):**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "role": "PATIENT"
}
```

---

### Patients (`/patients`)

#### Create Patient
```http
POST /patients
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "name": "Maria Silva",
  "weight": 70.0,
  "age": 30,
  "height": 175.0  // in cm
}
```

**Response (201):**
```json
{
  "patientId": "550e8400-e29b-41d4-a716-446655440002",
  "name": "Maria Silva",
  "imc": 22.86
}
```

#### Update Biometrics
```http
PUT /patients/{patientId}/biometrics
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "weight": 68.5,
  "age": 30,
  "height": 175.0
}
```

**Response (200):**
```json
{
  "patientId": "550e8400-e29b-41d4-a716-446655440002",
  "weight": 68.5,
  "imc": 22.40
}
```

---

### Training (`/training`)

#### Create Workout Plan
```http
POST /training/plans
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "patientId": "550e8400-e29b-41d4-a716-446655440002",
  "ptId": "550e8400-e29b-41d4-a716-446655440003"
}
```

**Response (201):**
```json
{
  "planId": "550e8400-e29b-41d4-a716-446655440004",
  "version": 1
}
```

#### Record Workout Execution
```http
POST /training/executions
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "patientId": "550e8400-e29b-41d4-a716-446655440002",
  "exerciseId": "550e8400-e29b-41d4-a716-446655440005",
  "actualSets": 4,
  "actualReps": 10,
  "actualWeight": 80.0,
  "feeling": "Good - felt strong",
  "status": "COMPLETED"
}
```

**Response (201):**
```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440006",
  "status": "COMPLETED"
}
```

---

### Nutrition (`/nutrition`)

#### Create Diet Plan
```http
POST /nutrition/plans
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "patientId": "550e8400-e29b-41d4-a716-446655440002",
  "nutritionistId": "550e8400-e29b-41d4-a716-446655440007"
}
```

**Response (201):**
```json
{
  "planId": "550e8400-e29b-41d4-a716-446655440008",
  "version": 1
}
```

---

## Security

### JWT Authentication
All protected endpoints require a Bearer token in the `Authorization` header:

```http
Authorization: Bearer <accessToken>
```

**Token expiration:**
- Access Token: 15 minutes
- Refresh Token: 7 days

### Rate Limiting
Authentication endpoints (`/auth/register`, `/auth/login`) are rate-limited to 5 requests per minute per IP address.

### User Enumeration Protection
Login and registration endpoints return generic error messages to prevent user enumeration attacks.

---

## Error Responses

### 400 Bad Request
```json
{
  "message": "Validation error: email: Invalid email format, password: Password must be at least 8 characters",
  "errorCode": "VALIDATION_ERROR"
}
```

### 401 Unauthorized
```json
{
  "message": "Invalid email or password",
  "errorCode": "AUTHENTICATION_ERROR"
}
```

### 409 Conflict
```json
{
  "message": "Email already registered",
  "errorCode": "DUPLICATE_EMAIL"
}
```

### 500 Internal Server Error
```json
{
  "message": "An error occurred. Please try again later.",
  "errorCode": "INTERNAL_ERROR"
}
```

---

## Environment Variables

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=fithub
DB_USER=fithub
DB_PASSWORD=fithub

# JWT
JWT_SECRET=your-secret-key-min-32-chars

# Server
SERVER_PORT=8080
```

---

## Testing with cURL

### Register
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "testPassword123",
    "name": "Test User",
    "role": "PATIENT"
  }'
```

### Login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "testPassword123"
  }'
```

### Create Patient (with token)
```bash
curl -X POST http://localhost:8080/patients \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "name": "Patient Name",
    "weight": 70.0,
    "age": 30,
    "height": 175.0
  }'
```

---

## Architecture

**API Layers:**
1. **Interfaces** (`/auth`, `/patients`, `/training`, `/nutrition`) - HTTP controllers
2. **Application** - Use cases orchestrating domain logic
3. **Domain** - Pure business logic (aggregates, value objects)
4. **Infrastructure** - Database, JWT, security

**Domain-Driven Design:**
- 4 bounded contexts: Identity, Patients, Training, Nutrition
- Append-only event sourcing for execution records
- Versioned plans (workout/diet) with history
- Immutable value objects (Email, Biometrics, Macros)

---

## Next Steps

- [ ] Implement repository layer with Spring Data JPA
- [ ] Add @DataJpaTest integration tests
- [ ] Add request/response compression
- [ ] Add API versioning (v1, v2, ...)
- [ ] Add health checks and monitoring
- [ ] Deploy with Docker
- [ ] Set up CI/CD pipeline (GitHub Actions)
