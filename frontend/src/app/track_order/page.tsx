'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { listOrders } from '@/api/orders';
import { getToken } from '@/api/token';
import type { Order } from '@/api/types';
import { formatJpy } from '@/lib/format';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';

export default function TrackOrder(): JSX.Element {
    const router = useRouter();
    const [orders, setOrders] = useState<Order[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (getToken() === null) {
            router.replace('/login');
            return;
        }
        let cancelled = false;
        listOrders()
            .then((o) => {
                if (!cancelled) setOrders(o);
            })
            .catch(() => {
                if (!cancelled) setError('Could not load your orders.');
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [router]);

    return (
        <div className='flex flex-col min-h-screen'>
            <Header />
            <div className='bg-primary p-6'>
                <h2 className='text-white'>Your Orders</h2>
            </div>
            <div className='flex-1 p-6 flex flex-col gap-4'>
                {loading && <p className='text-gray-400'>Loading…</p>}
                {error && <p className='text-red-500'>{error}</p>}
                {!loading && !error && orders.length === 0 && (
                    <div className='flex flex-col items-center gap-4 py-10 text-gray-400'>
                        <p>You have no orders yet.</p>
                        <Link href='/home' className='text-primary'>
                            Browse combos
                        </Link>
                    </div>
                )}
                {orders.map((order) => (
                    <Link
                        key={order.id}
                        href={`/complete_detail?orderId=${order.id}`}
                        className='flex flex-row justify-between items-center border rounded-lg p-4'
                    >
                        <div className='flex flex-col'>
                            <span className='font-semibold'>Order #{order.id}</span>
                            {order.orderDate && <span className='text-sm text-gray-400'>{order.orderDate}</span>}
                            {order.estimatedDeliveryDate && (
                                <span className='text-sm text-gray-400'>
                                    Est. delivery {order.estimatedDeliveryDate}
                                </span>
                            )}
                        </div>
                        <div className='flex flex-col items-end'>
                            <span className='badge badge-outline'>{order.status}</span>
                            <span className='text-sm'>{formatJpy(order.totalJpy)}</span>
                            {order.trackingNumber && (
                                <span className='text-xs text-gray-400 font-mono'>{order.trackingNumber}</span>
                            )}
                        </div>
                    </Link>
                ))}
            </div>
            <Footer />
        </div>
    );
}
