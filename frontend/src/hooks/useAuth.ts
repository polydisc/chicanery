'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { clearCartId, clearToken, getToken } from '@/api/token';

// Minimal client-side auth state backed by localStorage. No context/provider:
// the axios interceptor handles attaching the token and 401 redirects.
export function useAuth() {
    const router = useRouter();
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [ready, setReady] = useState(false);

    useEffect(() => {
        setIsAuthenticated(getToken() !== null);
        setReady(true);
    }, []);

    const logout = () => {
        clearToken();
        clearCartId();
        setIsAuthenticated(false);
        router.push('/login');
    };

    // Redirect to /login once we know there is no token. Call from pages that
    // require authentication.
    const requireAuth = () => {
        if (ready && !isAuthenticated) router.replace('/login');
    };

    return { isAuthenticated, ready, logout, requireAuth };
}
