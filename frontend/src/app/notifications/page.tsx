'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { listNotifications, markAllNotificationsRead, markNotificationRead } from '@/api/notifications';
import { getToken } from '@/api/token';
import type { Notification } from '@/api/types';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';

export default function NotificationsPage(): JSX.Element {
    const router = useRouter();
    const [notifications, setNotifications] = useState<Notification[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (getToken() === null) {
            router.replace('/login');
            return;
        }
        let cancelled = false;
        listNotifications()
            .then((n) => {
                if (!cancelled) setNotifications(n);
            })
            .catch(() => {
                if (!cancelled) setError('Could not load your notifications.');
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [router]);

    const onOpen = async (n: Notification) => {
        if (!n.read) {
            try {
                await markNotificationRead(n.id);
                setNotifications((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
            } catch {
                // Non-fatal: still navigate even if marking read fails.
            }
        }
        if (n.orderId != null) router.push(`/complete_detail?orderId=${n.orderId}`);
    };

    const onMarkAll = async () => {
        try {
            await markAllNotificationsRead();
            setNotifications((prev) => prev.map((x) => ({ ...x, read: true })));
        } catch {
            setError('Could not mark all as read.');
        }
    };

    return (
        <div className='flex flex-col min-h-screen'>
            <Header />
            <div className='bg-primary p-6 flex flex-row items-center justify-between'>
                <h2 className='text-white'>Notifications</h2>
                {notifications.some((n) => !n.read) && (
                    <button onClick={onMarkAll} className='text-white/90 text-sm underline'>
                        Mark all read
                    </button>
                )}
            </div>
            <div className='flex-1 p-6 flex flex-col gap-3'>
                {loading && <p className='text-gray-400'>Loading…</p>}
                {error && <p className='text-red-500'>{error}</p>}
                {!loading && !error && notifications.length === 0 && (
                    <p className='text-gray-400 py-10 text-center'>You have no notifications.</p>
                )}
                {notifications.map((n) => (
                    <button
                        key={n.id}
                        onClick={() => onOpen(n)}
                        className={`flex flex-col items-start gap-1 rounded-lg border p-4 text-left ${
                            n.read ? 'opacity-60' : 'border-primary'
                        }`}
                    >
                        <div className='flex flex-row items-center gap-2'>
                            {!n.read && <span className='h-2 w-2 rounded-full bg-primary' />}
                            <span className='text-sm'>{n.message}</span>
                        </div>
                        {n.createdAt && (
                            <span className='text-xs text-gray-400'>{new Date(n.createdAt).toLocaleString()}</span>
                        )}
                    </button>
                ))}
            </div>
            <Footer />
        </div>
    );
}
