# FitHub Frontend

React web application for personal training and nutrition platform.

## Setup

### Prerequisites
- Node.js 16+ 
- npm or yarn

### Installation

```bash
cd frontend
npm install
```

### Running Locally

**Terminal 1 - Backend:**
```bash
cd backend
mvn spring-boot:run
# Backend runs on http://localhost:8080
```

**Terminal 2 - Frontend:**
```bash
cd frontend
npm run dev
# Frontend runs on http://localhost:3000
```

Open http://localhost:3000 in your browser.

## Features Implemented

### Authentication ✓
- Login page with email/password
- Register page (Patient/PT/Nutritionist roles)
- JWT token storage in localStorage
- Auto-logout on 401 Unauthorized
- Protected routes

### Pages Implemented

#### Login & Register
- Email validation
- Password validation (min 8 chars)
- Role selection (Patient, PT, Nutritionist)
- Error handling

#### Dashboard
- Welcome message
- Quick links to modules
- User info display
- Navigation to main features

#### Patients Management (Partial)
- Create new patient
- Form with biometrics (weight, age, height)
- IMC calculation
- Patient list (UI ready, data pending)

#### Training & Nutrition
- Placeholder pages (ready for implementation)

## Architecture

```
frontend/
├── src/
│   ├── components/        # Reusable components
│   │   ├── Layout.jsx    # Main layout with navbar
│   │   └── Layout.css
│   ├── context/          # Global state
│   │   └── AuthContext.jsx
│   ├── pages/            # Page components
│   │   ├── LoginPage.jsx
│   │   ├── RegisterPage.jsx
│   │   ├── DashboardPage.jsx
│   │   ├── PatientsPage.jsx
│   │   ├── TrainingPage.jsx
│   │   ├── NutritionPage.jsx
│   │   └── *.css
│   ├── services/         # API integration
│   │   └── api.js
│   ├── App.jsx           # Routes
│   ├── main.jsx          # Entry point
│   └── index.css         # Global styles
├── index.html
├── package.json
└── vite.config.js
```

## API Integration

### Base URL
`http://localhost:8080`

### Endpoints Used

**Authentication**
- `POST /auth/register` — Create account
- `POST /auth/login` — Get access token
- `POST /auth/validate` — Verify token

**Patients**
- `POST /patients` — Create patient
- `GET /patients` — List patients (pending)

### Token Management
- Access token stored in `localStorage.accessToken`
- Refresh token stored in `localStorage.refreshToken`
- Auto-injected in request headers: `Authorization: Bearer <token>`

## Styling

- CSS modules per component
- Global styles in `index.css`
- CSS variables for theming
- Responsive design (mobile-first)
- Colors:
  - Primary: `#2563eb` (blue)
  - Secondary: `#10b981` (green)
  - Danger: `#ef4444` (red)

## Development

### Create New Page
1. Create file in `src/pages/YourPage.jsx`
2. Add route in `src/App.jsx`
3. Add navigation link in `src/components/Layout.jsx`

### Add API Call
1. Use `apiClient` from `src/services/api.js`
2. Example:
   ```jsx
   import { apiClient } from '../services/api'
   
   const response = await apiClient.get('/patients')
   ```

### Protected Routes
- Only authenticated users can access pages
- Login required redirects to `/login`
- Token validation on app load

## Build for Production

```bash
npm run build
npm run preview
```

Output goes to `dist/` directory.

## Next Steps

1. Implement Training page (create/view workout plans)
2. Implement Nutrition page (create/view diet plans)
3. Add patient detail page with biometric history
4. Add workout execution logging
5. Add meal execution logging
6. Add charts/analytics dashboard
7. Implement refresh token flow
8. Add form validation feedback
9. Add loading states
10. Add dark mode

## Troubleshooting

### CORS errors
- Make sure backend is running on port 8080
- Check vite.config.js proxy settings

### Token not persisting
- Clear localStorage and login again
- Check browser console for errors

### API calls failing
- Verify backend is running
- Check Authorization header in Network tab
- Verify token format: `Bearer <token>`

## Technologies

- **React 18** — UI framework
- **React Router 6** — Routing
- **Axios** — HTTP client
- **Vite** — Build tool
- **CSS3** — Styling (no external libraries)
