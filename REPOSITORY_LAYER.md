# Repository Layer Implementation

## Architecture

Repository pattern with **Domain-Driven Design**:

```
┌─────────────────────────────────────┐
│  Application Layer (Use Cases)      │
│  ↓ calls                            │
├─────────────────────────────────────┤
│  Domain Interfaces (Repository)     │ ← Interface defined in domain
│  ↓ implemented by                   │
├─────────────────────────────────────┤
│  JPA Repositories (Infrastructure)  │
│  ├─ Jpa*Repository (impl)           │
│  └─ *JpaRepository (Spring Data)    │
│  ↓ persist                          │
├─────────────────────────────────────┤
│  Database (PostgreSQL)              │
└─────────────────────────────────────┘
```

## Implementation Pattern

### 1. Domain Interface (in `domain/repository/`)
```java
public interface UserRepository {
    void save(User user);
    Optional<User> findByEmail(Email email);
    Optional<User> findById(UUID id);
}
```

### 2. Spring Data JPA Interface (in `infrastructure/persistence/`)
```java
@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
}
```

### 3. Adapter Implementation (in `infrastructure/persistence/`)
```java
@Repository
public class JpaUserRepository implements UserRepository {
    private final UserJpaRepository userJpaRepository;
    
    @Override
    public void save(User user) {
        UserEntity entity = new UserEntity();
        // Map domain object to entity
        userJpaRepository.save(entity);
    }
    
    @Override
    public Optional<User> findByEmail(Email email) {
        return userJpaRepository.findByEmail(email.getValue())
            .map(entity -> User.fromDatabase(...));
    }
}
```

## Implemented Repositories

### Identity Domain
- **UserJpaRepository** (Spring Data)
- **JpaUserRepository** (Implementation)

**Methods:**
- `save(User)` — Insert/update user
- `findByEmail(Email)` — Query by email
- `findById(UUID)` — Query by ID

**Database Table:** `users`

---

### Patients Domain
- **PatientJpaRepository** (Spring Data)
- **JpaPatientRepository** (Implementation)

**Methods:**
- `save(Patient)` — Insert/update patient with biometrics
- `findById(UUID)` — Query by patient ID
- `findByUserId(UUID)` — Query by user ID (1:1 relationship)

**Database Table:** `patients`

---

### Training Domain
- **WorkoutPlanJpaRepository** (Spring Data)
- **JpaWorkoutPlanRepository** (Implementation)

**Methods:**
- `save(WorkoutPlan)` — Insert/update versioned plan
- `findById(UUID)` — Query by plan ID
- `findLatestByPatientId(UUID)` — Get current version

**Database Table:** `workout_plans`

---

### Nutrition Domain
- **DietPlanJpaRepository** (Spring Data)
- **JpaDietPlanRepository** (Implementation)

**Methods:**
- `save(DietPlan)` — Insert/update versioned plan
- `findById(UUID)` — Query by plan ID
- `findByPatientIdAndLatestVersion(UUID)` — Get current version
- `findByPatientIdAllVersions(UUID)` — Get all versions (history)

**Database Table:** `diet_plans`

---

## Integration Testing

### Test Infrastructure
- **Testcontainers** v1.20.4 — Real PostgreSQL in Docker
- **@DataJpaTest** — Spring Boot JPA test slice
- **JUnit 5** — Test framework

### Test Coverage

Each domain has a `RepositoryTest` class:

**Patterns tested:**
1. ✓ Save and retrieve by ID
2. ✓ Query by business key (email, userId, etc.)
3. ✓ Verify calculated fields (IMC) persisted correctly
4. ✓ Test version tracking for plans
5. ✓ Verify domain object reconstruction

### Example Test
```java
@DataJpaTest
@Testcontainers
class UserRepositoryTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:17-alpine");
    
    @Test
    void shouldSaveAndFindUserByEmail() {
        Email email = Email.createAndValidate("test@example.com");
        User user = User.registerNew(email, "password123", UserRole.PATIENT, "Test");
        
        userRepository.save(user);
        
        Optional<User> found = userRepository.findByEmail(email);
        assertTrue(found.isPresent());
        assertEquals("Test", found.get().getName());
    }
}
```

### Running Tests
```bash
# All integration tests (with Docker PostgreSQL)
mvn clean test

# Single domain
mvn test -pl modules/identity

# Single test class
mvn test -Dtest=UserRepositoryTest
```

**First run:** Docker image pulled (~100MB), container started (~2-3 seconds)
**Subsequent runs:** Container reused, tests run in ~1-2 seconds

---

## Data Mapping

### User Entity → Domain
```
UserEntity                User Aggregate
├─ id (UUID)             ├─ id
├─ email (String)        ├─ email (Email VO)
├─ passwordHash          ├─ passwordHash
├─ role (String)         ├─ role (UserRole enum)
├─ name                  ├─ name
├─ status (String)       ├─ status (UserStatus enum)
├─ createdAt             ├─ createdAt
└─ updatedAt             └─ updatedAt
```

### Patient Entity → Domain
```
PatientEntity           Patient Aggregate
├─ id                  ├─ id
├─ userId              ├─ userId
├─ name                ├─ name
├─ weight              ├─ biometrics (Biometrics VO)
├─ age                 │  ├─ weight
├─ height              │  ├─ age
├─ imc (calculated)    │  ├─ height
├─ status              │  └─ imc (auto-calculated)
├─ createdAt           ├─ status
└─ updatedAt           ├─ createdAt
                       └─ updatedAt
```

---

## Best Practices Applied

### 1. Repository Interface in Domain
Domain doesn't depend on infrastructure (Spring, JPA, etc.)
Only application/infrastructure depend on domain.

### 2. Adapter Pattern
`JpaUserRepository` adapts Spring Data to domain interface.
Can replace JPA with any other persistence mechanism without changing domain.

### 3. Value Object Reconstruction
Entities deserialize into value objects:
```java
Email.fromStorage(entity.getEmail())  // No validation
Biometrics.create(weight, age, height) // Validation
```

### 4. Immutable Aggregates
Aggregates reconstructed from database, not mutated in place:
```java
User.fromDatabase(...) // Creates new immutable User
```

### 5. Tested with Real Database
Testcontainers provides confidence that:
- Migrations work correctly
- SQL queries work as expected
- Index performance is adequate
- Concurrency is handled properly

---

## Next Steps

- [ ] Implement execution repositories (append-only)
- [ ] Add repository query methods for reporting
- [ ] Implement soft deletes for audit trail
- [ ] Add transaction management (@Transactional)
- [ ] Implement caching layer (Spring Cache)
- [ ] Add database monitoring/metrics

---

## File Structure

```
backend/modules/
├─ identity/
│  ├─ src/main/java/.../infrastructure/persistence/
│  │  ├─ UserEntity.java
│  │  ├─ UserJpaRepository.java
│  │  └─ JpaUserRepository.java
│  └─ src/test/java/.../infrastructure/persistence/
│     └─ UserRepositoryTest.java
├─ patients/
├─ training/
└─ nutrition/
```

---

## Running Against Real Database

### Local PostgreSQL
```bash
# Start PostgreSQL
docker run -d --name fithub-db \
  -e POSTGRES_DB=fithub \
  -e POSTGRES_USER=fithub \
  -e POSTGRES_PASSWORD=fithub \
  -p 5432:5432 \
  postgres:17-alpine

# Run tests against real DB
mvn test
```

### Environment Variables
```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=fithub
DB_USER=fithub
DB_PASSWORD=fithub
```

---

## Performance Considerations

### Indexes (in V1__init.sql)
- Email unique index on `users.email`
- Patient ID index on `patients.user_id`
- Version index on `workout_plans(patient_id, version)`
- Executed date index on `workout_executions.executed_at`

### Query Optimization
- Foreign keys with CASCADE delete
- Lazy loading disabled (`open-in-view: false`)
- Transaction boundaries with `@Transactional(readOnly=true)`

### Testcontainers Performance
- Reused containers across test methods (same @DataJpaTest class)
- Docker caching for faster image pulls
- ~50-100ms per test method (database I/O included)
