// Lightweight JWT + cart-id persistence over localStorage.
// SSR-guarded: every access checks for `window` so these are safe to import
// from client components that may be evaluated during server rendering.

const TOKEN_KEY = 'auth_token';
const CART_ID_KEY = 'cart_id';

function read(key: string): string | null {
    if (typeof window === 'undefined') return null;
    return window.localStorage.getItem(key);
}

function write(key: string, value: string | null): void {
    if (typeof window === 'undefined') return;
    if (value === null) window.localStorage.removeItem(key);
    else window.localStorage.setItem(key, value);
}

export const getToken = (): string | null => read(TOKEN_KEY);
export const setToken = (token: string): void => write(TOKEN_KEY, token);
export const clearToken = (): void => write(TOKEN_KEY, null);

export const getCartId = (): number | null => {
    const raw = read(CART_ID_KEY);
    if (raw === null) return null;
    const id = Number(raw);
    // Cart ids are positive; treat 0/NaN/empty as "no cart".
    return Number.isInteger(id) && id > 0 ? id : null;
};
export const setCartId = (id: number): void => write(CART_ID_KEY, String(id));
export const clearCartId = (): void => write(CART_ID_KEY, null);
