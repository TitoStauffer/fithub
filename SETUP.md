# FitHub - Setup & Execution Guide

## 📋 Pré-requisitos

- Java 21+
- Node.js 18+
- PostgreSQL 17+
- Git

## 🏗️ Backend Setup

### 1. Banco de Dados
```bash
# Create database
createdb fithub
```

### 2. Variáveis de Ambiente
Create `.env` file na raiz do projeto:
```bash
JWT_SECRET=your-super-secret-key-here-change-in-production
DATABASE_URL=jdbc:postgresql://localhost:5432/fithub
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres
```

Or set as environment variables:
```bash
export JWT_SECRET=your-super-secret-key
export DATABASE_URL=jdbc:postgresql://localhost:5432/fithub
```

### 3. Build & Run Backend
```bash
cd backend
mvn clean install
cd boot
mvn spring-boot:run
```

Backend runs on: `http://localhost:8080`
API Docs: `http://localhost:8080/swagger-ui.html`

## 🎨 Frontend Setup

### 1. Install Dependencies
```bash
cd frontend
npm install
```

### 2. Run Development Server
```bash
npm run dev
```

Frontend runs on: `http://localhost:5173`

## 📱 Using FitHub

### 1. Register
- Go to `http://localhost:5173/register`
- Create account as Personal Trainer (PT) or Nutritionist
- Email: any valid email format
- Password: any string (BCrypt hashed)

### 2. Professional Dashboard
- Main page shows client statistics
- Quick view of total clients and active clients
- Links to individual client pages

### 3. Client Management
- Click "Ver Detalhes" (View Details) on any client
- Tabs available:
  - **Perfil**: Client info (peso, idade, altura, IMC, status)
  - **Progresso**: Weight history chart and detailed metrics
  - **Treinos**: Workout execution CRUD
    - Register exercises with sets, reps, weight
    - Log workout feeling (mood indicator)
    - Track completion status (COMPLETED, PARTIAL, SKIPPED)
  - **Refeições**: Meal execution CRUD
    - Register meals with macros (protein, carbs, fat)
    - Auto-calculated calories
    - Mark as completed or partial

### 4. Tracking Features
- Weight progress charts with 30-day view
- IMC evolution tracking
- Individual workout history per client
- Meal logging with calorie calculations
- Status indicators for completed/partial activities

## 🗄️ Database Schema

Tables created automatically via Flyway migrations:
- `users` - Authentication & profiles
- `patients` - Client/patient data
- `patient_biometric_history` - Weight tracking
- `patient_health_restrictions` - Health conditions
- `workout_plans` - Training templates
- `workout_executions` - Training logs
- `diet_plans` - Nutrition templates
- `meal_executions` - Meal logs

## 🔐 Security Features

- JWT Authentication (15min access tokens, 7-day refresh)
- BCrypt password hashing
- Rate limiting (5 requests/min on auth endpoints)
- User enumeration protection
- CSRF protection
- Stateless session management

## 🧪 Testing

### Backend Tests
```bash
cd backend
mvn test
```

Includes:
- Unit tests (domain layer)
- Integration tests (repositories with testcontainers)
- Use case tests (application layer)

### Frontend Tests
```bash
cd frontend
npm run test
```

## 📊 API Endpoints

| Method | Endpoint | Auth Required | Purpose |
|--------|----------|---|---|
| POST | `/auth/register` | No | Register user |
| POST | `/auth/login` | No | Login & get tokens |
| POST | `/auth/validate` | Yes | Validate JWT token |
| POST | `/patients` | Yes | Create patient |
| GET | `/patients/{id}` | Yes | Get patient details |
| PUT | `/patients/{id}/biometrics` | Yes | Update weight |
| POST | `/training/executions` | Yes | Log workout |
| POST | `/nutrition/executions` | Yes | Log meal |

## 🐛 Troubleshooting

### Backend won't start
- Check `JWT_SECRET` environment variable is set
- Verify PostgreSQL is running: `psql -U postgres`
- Check port 8080 is not in use

### Frontend can't connect to backend
- Ensure backend is running on `http://localhost:8080`
- Check browser console for CORS errors
- Verify Vite proxy config in `vite.config.js`

### Database migration errors
- Drop and recreate database: `dropdb fithub && createdb fithub`
- Flyway will auto-run migrations on next startup

### Token expired?
- Clear localStorage in browser developer tools
- Login again to get fresh tokens

## 📝 Project Structure

```
fithub/
├── backend/
│   ├── pom.xml (Parent)
│   ├── modules/
│   │   ├── identity/ (Auth & users)
│   │   ├── patients/ (Client management)
│   │   ├── training/ (Workout plans)
│   │   └── nutrition/ (Diet plans)
│   ├── platform/ (Database config)
│   └── boot/ (Spring Boot app)
└── frontend/
    ├── src/
    │   ├── context/ (Auth state)
    │   ├── services/ (API client)
    │   ├── components/ (Shared UI)
    │   └── pages/ (Page components)
    └── vite.config.js
```

## 🚀 Next Steps

- [ ] Connect individual client creation
- [ ] Implement workout plan templates
- [ ] Add nutrition plan templates  
- [ ] Create analytics dashboard
- [ ] Add progress notifications
- [ ] Implement biometric alerts

## 💡 Tips

- Professional dashboard shows mock data - connect to real API endpoints
- Workout/meal forms use mock submissions - implement backend integration
- Progress chart uses mock 30-day data - integrate with real biometric history
- Add loading indicators when fetching real data
- Implement error handling for API failures

## 📞 Support

For issues or questions, check:
1. Backend logs: `mvn spring-boot:run` console output
2. Frontend console: Browser DevTools → Console tab
3. Network tab: Check API responses and errors
4. Database: `psql -U postgres -d fithub` for direct queries
