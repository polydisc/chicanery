import { apiClient } from './client';
import type { CreateOrder, Order, PaymentRequest } from './types';

export async function createOrder(req: CreateOrder): Promise<Order> {
    const { data } = await apiClient.post<Order>('/orders', req);
    return data;
}

export async function listOrders(): Promise<Order[]> {
    const { data } = await apiClient.get<Order[]>('/orders');
    return data;
}

export async function getOrder(orderId: number): Promise<Order> {
    const { data } = await apiClient.get<Order>(`/orders/${orderId}`);
    return data;
}

export async function payOrder(orderId: number, req: PaymentRequest): Promise<Order> {
    const { data } = await apiClient.post<Order>(`/orders/${orderId}/payment`, req);
    return data;
}

export async function cancelOrder(orderId: number): Promise<Order> {
    const { data } = await apiClient.post<Order>(`/orders/${orderId}/cancel`);
    return data;
}

// An order can be cancelled until it ships. This only gates the button's
// visibility — the server is authoritative (returns 409 if not cancellable).
// Keep in sync with OrderRepository.CancellableStatuses on the backend.
export const CANCELLABLE_STATUSES = ['NEW', 'PROCESSING', 'HOLD'];
