import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';

import { App } from './App';
import { createKeycloakClient } from './features/auth/authClient';
import { AuthProvider } from './features/auth/AuthProvider';
import './index.css';

const queryClient = new QueryClient();
// Uma instância só: é ela que guarda os tokens em memória.
const authClient = createKeycloakClient();

const root = document.getElementById('root');
if (!root) throw new Error('Elemento #root ausente no index.html.');

createRoot(root).render(
    <StrictMode>
        <QueryClientProvider client={queryClient}>
            <AuthProvider client={authClient}>
                <BrowserRouter>
                    <App />
                </BrowserRouter>
            </AuthProvider>
        </QueryClientProvider>
    </StrictMode>,
);
