'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { CANCELLABLE_STATUSES, cancelOrder, getOrder } from '@/api/orders';
import { getToken } from '@/api/token';
import type { Order } from '@/api/types';
import { formatJpy } from '@/lib/format';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import Button from '@/components/ui/Button';

// The normal order lifecycle, in order. CANCELLED is shown separately.
const STATUS_STEPS = ['NEW', 'PROCESSING', 'SHIPPED', 'DELIVERED'] as const;

const STATUS_LABELS: Record<string, string> = {
    NEW: 'Placed',
    PROCESSING: 'Paid',
    SHIPPED: 'Shipped',
    DELIVERED: 'Delivered',
    CANCELLED: 'Cancelled'
};

function StatusSection({ status }: { status: string }): JSX.Element {
    if (status === 'CANCELLED') {
        return (
            <section className='flex flex-col gap-2'>
                <h4 className='font-semibold'>Status</h4>
                <span className='self-start rounded-full bg-red-100 px-3 py-1 text-sm text-red-600'>Cancelled</span>
            </section>
        );
    }
    const current = STATUS_STEPS.indexOf(status as (typeof STATUS_STEPS)[number]);
    return (
        <section className='flex flex-col gap-2'>
            <h4 className='font-semibold'>Status</h4>
            <div className='flex flex-row items-center'>
                {STATUS_STEPS.map((step, i) => {
                    const reached = current >= 0 && i <= current;
                    return (
                        <div key={step} className='flex flex-1 flex-col items-center'>
                            <div className='flex w-full items-center'>
                                <div
                                    className={`h-1 flex-1 ${i === 0 ? 'bg-transparent' : reached ? 'bg-primary' : 'bg-gray-200'}`}
                                />
                                <div
                                    className={`h-3 w-3 rounded-full ${reached ? 'bg-primary' : 'bg-gray-300'}`}
                                    aria-current={i === current ? 'step' : undefined}
                                />
                                <div
                                    className={`h-1 flex-1 ${
                                        i === STATUS_STEPS.length - 1
                                            ? 'bg-transparent'
                                            : current > i
                                              ? 'bg-primary'
                                              : 'bg-gray-200'
                                    }`}
                                />
                            </div>
                            <span className={`mt-1 text-xs ${reached ? 'text-primary' : 'text-gray-400'}`}>
                                {STATUS_LABELS[step]}
                            </span>
                        </div>
                    );
                })}
            </div>
        </section>
    );
}

function OrderConfirmation(): JSX.Element {
    const router = useRouter();
    const searchParams = useSearchParams();
    const orderId = Number(searchParams.get('orderId'));

    const [order, setOrder] = useState<Order | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [cancelling, setCancelling] = useState(false);
    const [cancelError, setCancelError] = useState<string | null>(null);

    const onCancel = async () => {
        if (!order) return;
        if (!window.confirm('Cancel this order?')) return;
        setCancelError(null);
        setCancelling(true);
        try {
            const updated = await cancelOrder(order.id);
            setOrder(updated);
        } catch (err: unknown) {
            const status =
                typeof err === 'object' && err !== null && 'response' in err
                    ? (err as { response?: { status?: number } }).response?.status
                    : undefined;
            setCancelError(
                status === 409
                    ? 'This order can no longer be cancelled — it has shipped.'
                    : 'Could not cancel this order. Please try again.'
            );
        } finally {
            setCancelling(false);
        }
    };

    useEffect(() => {
        if (getToken() === null) {
            router.replace('/login');
            return;
        }
        if (!orderId || Number.isNaN(orderId)) {
            setError('Unknown order.');
            setLoading(false);
            return;
        }
        let cancelled = false;
        getOrder(orderId)
            .then((o) => {
                if (!cancelled) setOrder(o);
            })
            .catch(() => {
                if (!cancelled) setError('Could not load your order.');
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [orderId, router]);

    if (loading) {
        return <div className='flex h-screen items-center justify-center text-gray-400'>Loading…</div>;
    }
    if (error || !order) {
        return (
            <div className='flex h-screen items-center justify-center text-red-500'>{error ?? 'Order not found.'}</div>
        );
    }

    return (
        <div className='flex flex-col min-h-screen'>
            <Header />
            <div className='bg-primary p-6 text-center'>
                <h2 className='text-white'>Thank you!</h2>
                <p className='text-white/90'>
                    Order #{order.id} — {STATUS_LABELS[order.status] ?? order.status}
                </p>
            </div>
            <div className='flex-1 p-6 flex flex-col gap-6'>
                <StatusSection status={order.status} />
                <section className='flex flex-col gap-3'>
                    <h4 className='font-semibold'>Order summary</h4>
                    {order.items.map((item) => (
                        <div key={item.productId} className='flex flex-row justify-between'>
                            <span>
                                {item.productName} × {item.quantity}
                            </span>
                            <span>{formatJpy(item.lineTotalJpy)}</span>
                        </div>
                    ))}
                    <div className='flex flex-row justify-between font-semibold border-t pt-2'>
                        <span>Total</span>
                        <span>{formatJpy(order.totalJpy)}</span>
                    </div>
                    <p className='text-sm text-gray-400'>Shipping to: {order.shippingAddress}</p>
                    {(order.trackingNumber || order.estimatedDeliveryDate) && (
                        <div className='mt-2 flex flex-col gap-1 rounded-lg bg-gray-50 p-3 text-sm'>
                            <h5 className='font-semibold'>Shipment</h5>
                            {order.carrier && (
                                <div className='flex flex-row justify-between'>
                                    <span className='text-gray-400'>Carrier</span>
                                    <span>{order.carrier}</span>
                                </div>
                            )}
                            {order.trackingNumber && (
                                <div className='flex flex-row justify-between'>
                                    <span className='text-gray-400'>Tracking #</span>
                                    <span className='font-mono'>{order.trackingNumber}</span>
                                </div>
                            )}
                            {order.shippedDate && (
                                <div className='flex flex-row justify-between'>
                                    <span className='text-gray-400'>Shipped</span>
                                    <span>{order.shippedDate}</span>
                                </div>
                            )}
                            {order.estimatedDeliveryDate && (
                                <div className='flex flex-row justify-between'>
                                    <span className='text-gray-400'>Estimated delivery</span>
                                    <span>{order.estimatedDeliveryDate}</span>
                                </div>
                            )}
                        </div>
                    )}
                </section>
            </div>
            <div className='p-6 flex flex-col gap-3'>
                {cancelError && <p className='text-red-500 text-sm text-center'>{cancelError}</p>}
                <Button onClick={() => router.push('/track_order')}>Track my orders</Button>
                {CANCELLABLE_STATUSES.includes(order.status) && (
                    <button
                        onClick={onCancel}
                        disabled={cancelling}
                        className='rounded-lg border border-red-400 py-3 text-red-500 text-sm disabled:opacity-50'
                    >
                        {cancelling ? 'Cancelling…' : 'Cancel order'}
                    </button>
                )}
                <Link href='/home' className='text-center text-primary text-sm'>
                    Continue shopping
                </Link>
            </div>
            <Footer />
        </div>
    );
}

export default function CompleteDetailPage(): JSX.Element {
    return (
        <Suspense fallback={null}>
            <OrderConfirmation />
        </Suspense>
    );
}
