import { apiClient } from './client';
import { getCartId, setCartId } from './token';
import type { AddCartItem, Cart } from './types';

export async function createCart(): Promise<Cart> {
    const { data } = await apiClient.post<Cart>('/cart');
    setCartId(data.id);
    return data;
}

export async function getCart(cartId: number): Promise<Cart> {
    const { data } = await apiClient.get<Cart>(`/cart/${cartId}`);
    return data;
}

export async function addItem(cartId: number, item: AddCartItem): Promise<Cart> {
    const { data } = await apiClient.post<Cart>(`/cart/${cartId}/items`, item);
    return data;
}

export async function setItemQuantity(cartId: number, productId: number, quantity: number): Promise<Cart> {
    const { data } = await apiClient.put<Cart>(`/cart/${cartId}/items/${productId}`, { quantity });
    return data;
}

// Return the caller's current cart id, creating a cart on first use.
export async function ensureCart(): Promise<number> {
    const existing = getCartId();
    if (existing !== null && !Number.isNaN(existing)) return existing;
    const cart = await createCart();
    return cart.id;
}
