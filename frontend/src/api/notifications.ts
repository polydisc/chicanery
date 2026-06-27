import { apiClient } from './client';
import type { Notification } from './types';

export async function listNotifications(): Promise<Notification[]> {
    const { data } = await apiClient.get<Notification[]>('/notifications');
    return data;
}

export async function markNotificationRead(notificationId: number): Promise<void> {
    await apiClient.post(`/notifications/${notificationId}/read`);
}

export async function markAllNotificationsRead(): Promise<void> {
    await apiClient.post('/notifications/read-all');
}

export function unreadCount(notifications: Notification[]): number {
    return notifications.filter((n) => !n.read).length;
}
