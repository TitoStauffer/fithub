// Matchers do jest-dom (toBeInTheDocument, toHaveTextContent...) e limpeza do DOM entre os testes.
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
    cleanup();
});
