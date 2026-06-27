'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { getCart } from '@/api/cart';
import { createOrder, payOrder } from '@/api/orders';
import { clearCartId, getCartId, getToken } from '@/api/token';
import type { PaymentRequest } from '@/api/types';
import { formatJpy } from '@/lib/format';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import Button from '@/components/ui/Button';
import InputText from '@/components/ui/InputText';

export default function Payment(): JSX.Element {
    const router = useRouter();
    const [cartId, setCartId] = useState<number | null>(null);
    const [orderId, setOrderId] = useState<number | null>(null);
    const [total, setTotal] = useState<number | null>(null);
    const [shippingAddress, setShippingAddress] = useState('');
    const [method, setMethod] = useState<PaymentRequest['method']>('card');
    const [cardNumber, setCardNumber] = useState('');
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (getToken() === null) {
            router.replace('/login');
            return;
        }
        const id = getCartId();
        if (id === null || Number.isNaN(id)) {
            router.replace('/basket');
            return;
        }
        setCartId(id);
        getCart(id)
            .then((c) => setTotal(c.totalJpy))
            .catch(() => setError('Could not load your basket.'));
    }, [router]);

    const onSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (cartId === null) return;
        setError(null);
        setSubmitting(true);
        try {
            // Create the order at most once: on a payment retry after a
            // partial failure, reuse the existing order instead of creating a
            // duplicate from the same cart.
            let id = orderId;
            if (id === null) {
                const order = await createOrder({ cartId, shippingAddress });
                id = order.id;
                setOrderId(id);
            }
            await payOrder(id, {
                method,
                details: method === 'card' ? cardNumber : undefined
            });
            clearCartId();
            router.push(`/complete_detail?orderId=${id}`);
        } catch {
            setError('Payment failed. Please check your details and try again.');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className='flex flex-col min-h-screen'>
            <Header />
            <div className='bg-primary p-6'>
                <h2 className='text-white'>Checkout</h2>
                {total !== null && <p className='text-white/90'>Total to pay: {formatJpy(total)}</p>}
            </div>
            <form onSubmit={onSubmit} className='flex-1 p-6 flex flex-col gap-4'>
                <div>
                    <label className='text-sm text-gray-500'>Shipping address</label>
                    <InputText
                        placeholder='1-1 Chiyoda, Chiyoda-ku, Tokyo'
                        value={shippingAddress}
                        onChange={(e) => setShippingAddress(e.target.value)}
                        required
                    />
                </div>
                <div>
                    <label className='text-sm text-gray-500'>Payment method</label>
                    <select
                        className='p-3 rounded-lg w-full text-black'
                        value={method}
                        onChange={(e) => setMethod(e.target.value as PaymentRequest['method'])}
                    >
                        <option value='card'>Card</option>
                        <option value='bank_transfer'>Bank transfer</option>
                        <option value='cod'>Cash on delivery</option>
                    </select>
                </div>
                {method === 'card' && (
                    <div>
                        <label className='text-sm text-gray-500'>Card number</label>
                        <InputText
                            inputMode='numeric'
                            placeholder='4242 4242 4242 4242'
                            value={cardNumber}
                            onChange={(e) => setCardNumber(e.target.value)}
                            required
                        />
                    </div>
                )}
                {error && <p className='text-red-500 text-sm'>{error}</p>}
                <div className='mt-auto pt-4'>
                    <Button type='submit' disabled={submitting}>
                        {submitting ? 'Processing…' : 'Pay now'}
                    </Button>
                </div>
            </form>
            <Footer />
        </div>
    );
}
