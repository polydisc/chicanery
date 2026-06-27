import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '@/test/msw/server';
import { deliverOrder, listAllOrders, shipOrder } from './admin';

const sampleOrder = {
    id: 10,
    items: [],
    totalJpy: 400,
    shippingAddress: 'Tokyo',
    status: 'PROCESSING',
    orderDate: '2026-06-27'
};

describe('admin orders api', () => {
    it('listAllOrders returns every order', async () => {
        server.use(http.get('*/api/v1/admin/orders', () => HttpResponse.json([sampleOrder])));
        const orders = await listAllOrders();
        expect(orders).toHaveLength(1);
        expect(orders[0]?.id).toBe(10);
    });

    it('shipOrder posts the tracking payload and returns the shipped order', async () => {
        let body: unknown = null;
        server.use(
            http.post('*/api/v1/admin/orders/10/ship', async ({ request }) => {
                body = await request.json();
                return HttpResponse.json({
                    ...sampleOrder,
                    status: 'SHIPPED',
                    trackingNumber: 'T1',
                    carrier: 'Yamato'
                });
            })
        );
        const updated = await shipOrder(10, { trackingNumber: 'T1', carrier: 'Yamato' });
        expect(body).toEqual({ trackingNumber: 'T1', carrier: 'Yamato' });
        expect(updated.status).toBe('SHIPPED');
        expect(updated.trackingNumber).toBe('T1');
    });

    it('deliverOrder marks a shipped order delivered', async () => {
        server.use(
            http.post('*/api/v1/admin/orders/10/deliver', () =>
                HttpResponse.json({ ...sampleOrder, status: 'DELIVERED' })
            )
        );
        const updated = await deliverOrder(10);
        expect(updated.status).toBe('DELIVERED');
    });
});
