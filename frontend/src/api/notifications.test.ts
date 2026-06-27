import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '@/test/msw/server';
import { listNotifications, markAllNotificationsRead, markNotificationRead, unreadCount } from './notifications';

const notifs = [
    { id: 1, notificationType: 'SHIPPED', message: 'Order #10 shipped.', read: false, orderId: 10 },
    { id: 2, notificationType: 'PAID', message: 'Payment received for order #10.', read: true, orderId: 10 }
];

describe('notifications api', () => {
    it('listNotifications returns the notifications', async () => {
        server.use(http.get('*/api/v1/notifications', () => HttpResponse.json(notifs)));
        const result = await listNotifications();
        expect(result).toHaveLength(2);
        expect(result[0]?.notificationType).toBe('SHIPPED');
    });

    it('unreadCount counts unread notifications', () => {
        expect(unreadCount(notifs)).toBe(1);
    });

    it('markNotificationRead POSTs to the read endpoint', async () => {
        let hit = false;
        server.use(
            http.post('*/api/v1/notifications/1/read', () => {
                hit = true;
                return new HttpResponse(null, { status: 204 });
            })
        );
        await markNotificationRead(1);
        expect(hit).toBe(true);
    });

    it('markAllNotificationsRead POSTs to read-all', async () => {
        let hit = false;
        server.use(
            http.post('*/api/v1/notifications/read-all', () => {
                hit = true;
                return new HttpResponse(null, { status: 204 });
            })
        );
        await markAllNotificationsRead();
        expect(hit).toBe(true);
    });
});
