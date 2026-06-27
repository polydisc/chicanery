import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { server } from '@/test/msw/server';
import { setToken, clearToken } from '@/api/token';
import Header from './Header';

vi.mock('next/navigation', () => ({
    usePathname: () => '/home',
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
}));

// next/font/google isn't available in the jsdom test environment.
vi.mock('@/lib/fonts', () => ({ brandFont: { className: 'brand-font' } }));

const notAdmin = http.get('*/api/v1/admin/me', () => new HttpResponse(null, { status: 403 }));

describe('Header', () => {
    beforeEach(() => setToken('test-token'));
    afterEach(() => clearToken());

    it('renders the persistent nav links and log out', async () => {
        server.use(
            notAdmin,
            http.get('*/api/v1/notifications', () => HttpResponse.json([]))
        );
        render(<Header />);
        // The brand links home (there is no separate "Home" item).
        expect(screen.getByRole('link', { name: 'Chicanery' })).toHaveAttribute('href', '/home');
        expect(screen.getByRole('link', { name: 'Orders' })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Basket' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
    });

    it('shows the unread notification badge', async () => {
        server.use(
            notAdmin,
            http.get('*/api/v1/notifications', () =>
                HttpResponse.json([
                    { id: 1, notificationType: 'SHIPPED', message: 'x', read: false },
                    { id: 2, notificationType: 'PAID', message: 'y', read: true }
                ])
            )
        );
        render(<Header />);
        expect(await screen.findByText('1')).toBeInTheDocument();
    });

    it('shows the Admin link only for admins', async () => {
        server.use(
            http.get('*/api/v1/admin/me', () => new HttpResponse(null, { status: 200 })),
            http.get('*/api/v1/notifications', () => HttpResponse.json([]))
        );
        render(<Header />);
        expect(await screen.findByRole('link', { name: 'Admin' })).toBeInTheDocument();
    });
});
