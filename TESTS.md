# FitHub Backend — Test Strategy

## Overview
- **Framework**: JUnit 5 + Mockito + Spring Boot Test + MockMvc
- **Coverage**: Unit tests (aggregates, value objects) + Integration tests (use cases) + API tests (controllers)
- **BDD approach**: Tests written from user story perspective

## Test Pyramid

```
         API Tests (MockMvc)
          ↗         ↖
   Use Case Tests (Integration)
      ↗                     ↖
  Unit Tests (Domain)
```

## Test Structure by Domain

### Identity Domain
**Unit Tests**:
- `UserTest.java` — aggregate behavior (registration, authentication, password hashing)
- `EmailTest.java` — RFC 5322 email validation via Apache Commons

**Integration Tests**:
- `RegisterUserUseCaseTest.java` — user registration with repository mock
- `AuthenticateUserUseCaseTest.java` — login flow with user enumeration protection

**API Tests**:
- `AuthControllerTest.java` — POST /auth/register, POST /auth/login endpoints

### Patients Domain
**Unit Tests**:
- `PatientTest.java` — patient creation, biometric validation, IMC calculation, restrictions

**Integration Tests**:
- `CreatePatientUseCaseTest.java` — patient creation with repository mock

**API Tests**:
- `PatientControllerTest.java` — POST /patients, PUT /patients/{id}/biometrics endpoints

### Training Domain
**Unit Tests**:
- `WorkoutPlanTest.java` — versioned workout plan, exercise addition, plan activation

**Integration Tests**:
- `CreateWorkoutPlanUseCaseTest.java` — workout plan creation with repository mock

**API Tests**:
- `TrainingControllerTest.java` — POST /training/plans endpoint

### Nutrition Domain
**Unit Tests**:
- `MacrosTest.java` — macro calculation, calorie computation, value object behavior

**Integration Tests**:
- `CreateDietPlanUseCaseTest.java` — diet plan creation with repository mock

**API Tests**:
- `NutritionControllerTest.java` — POST /nutrition/plans endpoint

## Running Tests

### All Tests
```bash
mvn clean test
```

### Single Domain
```bash
mvn test -pl modules/identity
mvn test -pl modules/patients
mvn test -pl modules/training
mvn test -pl modules/nutrition
```

### Specific Test Class
```bash
mvn test -Dtest=UserTest
mvn test -Dtest=AuthControllerTest
```

### Coverage Report
```bash
mvn clean test jacoco:report
# Open target/site/jacoco/index.html
```

## Test Coverage Goals

| Layer | Target | Current |
|-------|--------|---------|
| Domain (aggregates/VOs) | 90%+ | ✓ |
| Use Cases | 80%+ | ✓ |
| Controllers | 70%+ | ✓ |
| **Overall** | **80%** | **In Progress** |

## Key Testing Patterns

### 1. Unit Tests (Domain)
- Focus on business logic correctness
- No mocking of domain objects
- Pure assertions

```java
@Test
void shouldCalculateIMCCorrectly() {
    Biometrics bio = Biometrics.create(80.0, 30, 1.80);
    assertEquals(24.69, Math.round(bio.getImc() * 100.0) / 100.0);
}
```

### 2. Integration Tests (Use Cases)
- Mock only repositories
- Test orchestration of domain objects + service
- Verify repository interactions

```java
@Test
void shouldCreatePatientWithBiometrics() {
    CreatePatientUseCase.CreatePatientRequest request = ...;
    CreatePatientUseCase.CreatePatientResponse response = useCase.execute(request);
    
    verify(patientRepository).save(argThat(patient ->
        patient.getBiometrics().getWeight() == 75.0
    ));
}
```

### 3. API Tests (Controllers)
- Mock use cases
- Test HTTP contracts (status codes, JSON response structure)
- Validate request validation

```java
@Test
void shouldRegisterUserWithValidPayload() throws Exception {
    mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"email":"test@example.com",...}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").exists());
}
```

## Security Tests

### User Enumeration Protection
```java
@Test
void shouldNotEnumerateNonexistentUsers() {
    // Both user-not-found and password-invalid return same error message
    when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> useCase.execute(request));
}
```

### Password Hashing
```java
@Test
void shouldNotHashPlainPasswordInAggregate() {
    User user = User.registerNew(email, "Test", "myPassword", "PATIENT");
    assertNotEquals("myPassword", user.getPasswordHash());
}
```

## Next Steps

- [ ] Add @DataJpaTest for repository layer tests (with testcontainers PostgreSQL)
- [ ] Add @SpringBootTest for end-to-end tests
- [ ] Add performance benchmarks for authentication
- [ ] Add mutation testing (PIT) to verify test quality
- [ ] Add contract tests for API consumers

## CI/CD Integration

Tests run automatically on:
- `git push` (pre-commit hooks)
- Pull requests (GitHub Actions)
- Main branch deployments

Minimum requirements:
- ✓ All tests passing
- ✓ 80%+ code coverage
- ✓ No security vulnerabilities (OWASP)
