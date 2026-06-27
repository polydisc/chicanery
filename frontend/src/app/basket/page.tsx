'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { getCart, setItemQuantity } from '@/api/cart';
import { getCartId, getToken } from '@/api/token';
import type { Cart } from '@/api/types';
import { formatJpy } from '@/lib/format';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import Button from '@/components/ui/Button';

export default function Basket(): JSX.Element {
    const router = useRouter();
    const [cart, setCart] = useState<Cart | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [updating, setUpdating] = useState(false);

    useEffect(() => {
        if (getToken() === null) {
            router.replace('/login');
            return;
        }
        const cartId = getCartId();
        if (cartId === null || Number.isNaN(cartId)) {
            setLoading(false);
            return;
        }
        let cancelled = false;
        getCart(cartId)
            .then((c) => {
                if (!cancelled) setCart(c);
            })
            .catch(() => {
                if (!cancelled) setError('Could not load your basket.');
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [router]);

    const changeQuantity = async (productId: number, quantity: number) => {
        if (!cart) return;
        setUpdating(true);
        try {
            const updated = await setItemQuantity(cart.id, productId, quantity);
            setCart(updated);
        } catch {
            setError('Could not update the basket.');
        } finally {
            setUpdating(false);
        }
    };

    if (loading) {
        return <div className='flex h-screen items-center justify-center text-gray-400'>Loading…</div>;
    }

    const isEmpty = !cart || cart.items.length === 0;

    return (
        <div className='flex flex-col min-h-screen'>
            <Header />
            <div className='bg-primary p-6'>
                <h2 className='text-white'>Your Basket</h2>
            </div>
            <div className='flex-1 p-6 flex flex-col gap-4'>
                {error && <p className='text-red-500'>{error}</p>}
                {isEmpty ? (
                    <div className='flex flex-col items-center gap-4 py-10 text-gray-400'>
                        <p>Your basket is empty.</p>
                        <Link href='/home' className='text-primary'>
                            Browse combos
                        </Link>
                    </div>
                ) : (
                    <>
                        {cart!.items.map((item) => (
                            <div
                                key={item.productId}
                                className='flex flex-row justify-between items-center border-b pb-3'
                            >
                                <div className='flex flex-col'>
                                    <span>{item.productName}</span>
                                    <span className='text-sm text-gray-400'>{formatJpy(item.unitPriceJpy)} each</span>
                                </div>
                                <div className='flex flex-row items-center gap-3'>
                                    <button
                                        className='btn btn-circle btn-sm'
                                        aria-label='Decrease quantity'
                                        disabled={updating}
                                        onClick={() => changeQuantity(item.productId, item.quantity - 1)}
                                    >
                                        −
                                    </button>
                                    <span>{item.quantity}</span>
                                    <button
                                        className='btn btn-circle btn-sm'
                                        aria-label='Increase quantity'
                                        disabled={updating}
                                        onClick={() => changeQuantity(item.productId, item.quantity + 1)}
                                    >
                                        +
                                    </button>
                                    <span className='w-20 text-right'>{formatJpy(item.lineTotalJpy)}</span>
                                </div>
                            </div>
                        ))}
                        <div className='flex flex-row justify-between font-semibold pt-2'>
                            <span>Total</span>
                            <span>{formatJpy(cart!.totalJpy)}</span>
                        </div>
                    </>
                )}
            </div>
            {!isEmpty && (
                <div className='p-6'>
                    <Button onClick={() => router.push('/payment')}>Checkout</Button>
                </div>
            )}
            <Footer />
        </div>
    );
}
