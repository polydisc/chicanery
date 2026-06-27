import { apiClient } from './client';
import type { Order, Product, ShipOrder } from './types';
import type { components } from './schema';

export type ProductInput = components['schemas']['ProductInput'];

// Resolves to true if the caller is an admin (GET /admin/me → 200), false on 403.
// This only gates the admin UI — the server enforces authorization on every
// admin endpoint (403 for non-admins), so it's not a security boundary itself.
export async function checkAdmin(): Promise<boolean> {
    try {
        await apiClient.get('/admin/me');
        return true;
    } catch {
        return false;
    }
}

export async function createProduct(input: ProductInput): Promise<Product> {
    const { data } = await apiClient.post<Product>('/admin/products', input);
    return data;
}

export async function updateProduct(id: number, input: ProductInput): Promise<Product> {
    const { data } = await apiClient.put<Product>(`/admin/products/${id}`, input);
    return data;
}

export async function deleteProduct(id: number): Promise<void> {
    await apiClient.delete(`/admin/products/${id}`);
}

export async function blockUser(userId: number): Promise<void> {
    await apiClient.post(`/admin/users/${userId}/block`);
}

export async function unblockUser(userId: number): Promise<void> {
    await apiClient.post(`/admin/users/${userId}/unblock`);
}

export async function listAllOrders(): Promise<Order[]> {
    const { data } = await apiClient.get<Order[]>('/admin/orders');
    return data;
}

// Mark a PROCESSING order as SHIPPED, recording tracking/carrier/ETA.
export async function shipOrder(orderId: number, req: ShipOrder): Promise<Order> {
    const { data } = await apiClient.post<Order>(`/admin/orders/${orderId}/ship`, req);
    return data;
}

// Mark a SHIPPED order as DELIVERED.
export async function deliverOrder(orderId: number): Promise<Order> {
    const { data } = await apiClient.post<Order>(`/admin/orders/${orderId}/deliver`);
    return data;
}
