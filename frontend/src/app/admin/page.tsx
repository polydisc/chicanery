'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
    blockUser,
    checkAdmin,
    createProduct,
    deleteProduct,
    deliverOrder,
    listAllOrders,
    shipOrder,
    unblockUser
} from '@/api/admin';
import { searchProducts } from '@/api/products';
import { getToken } from '@/api/token';
import type { Order, Product } from '@/api/types';
import { formatJpy } from '@/lib/format';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import Button from '@/components/ui/Button';
import InputText from '@/components/ui/InputText';

// One row in the admin order list. PROCESSING orders get a ship form (tracking
// number, carrier, ETA); SHIPPED orders get a "Mark delivered" button.
function AdminOrderRow({ order, onChanged }: { order: Order; onChanged: () => void }): JSX.Element {
    const [trackingNumber, setTrackingNumber] = useState('');
    const [carrier, setCarrier] = useState('');
    const [eta, setEta] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const onShip = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        setBusy(true);
        try {
            await shipOrder(order.id, {
                trackingNumber,
                carrier,
                estimatedDeliveryDate: eta || undefined
            });
            onChanged();
        } catch {
            setError('Could not ship this order.');
        } finally {
            setBusy(false);
        }
    };

    const onDeliver = async () => {
        setError(null);
        setBusy(true);
        try {
            await deliverOrder(order.id);
            onChanged();
        } catch {
            setError('Could not mark this order delivered.');
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className='flex flex-col gap-2 border rounded-lg p-3'>
            <div className='flex flex-row justify-between items-center'>
                <span className='font-semibold'>Order #{order.id}</span>
                <span className='badge badge-outline'>{order.status}</span>
            </div>
            <span className='text-sm text-gray-400'>
                {formatJpy(order.totalJpy)} · {order.shippingAddress}
            </span>
            {order.trackingNumber && (
                <span className='text-xs text-gray-400 font-mono'>
                    {order.carrier} {order.trackingNumber}
                    {order.estimatedDeliveryDate ? ` · ETA ${order.estimatedDeliveryDate}` : ''}
                </span>
            )}
            {error && <span className='text-red-500 text-sm'>{error}</span>}
            {order.status === 'PROCESSING' && (
                <form onSubmit={onShip} className='flex flex-col gap-2'>
                    <InputText
                        placeholder='Tracking number'
                        value={trackingNumber}
                        onChange={(e) => setTrackingNumber(e.target.value)}
                        required
                    />
                    <InputText
                        placeholder='Carrier'
                        value={carrier}
                        onChange={(e) => setCarrier(e.target.value)}
                        required
                    />
                    <InputText
                        type='date'
                        placeholder='Estimated delivery (optional)'
                        value={eta}
                        onChange={(e) => setEta(e.target.value)}
                    />
                    <Button type='submit' disabled={busy}>
                        {busy ? 'Shipping…' : 'Mark shipped'}
                    </Button>
                </form>
            )}
            {order.status === 'SHIPPED' && (
                <button
                    onClick={onDeliver}
                    disabled={busy}
                    className='text-primary text-sm border border-primary rounded px-3 py-2 disabled:opacity-50'
                >
                    {busy ? 'Updating…' : 'Mark delivered'}
                </button>
            )}
        </div>
    );
}

export default function AdminPage(): JSX.Element {
    const router = useRouter();
    const [authorized, setAuthorized] = useState<boolean | null>(null);
    const [products, setProducts] = useState<Product[]>([]);
    const [orders, setOrders] = useState<Order[]>([]);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);

    // New-product form.
    const [name, setName] = useState('');
    const [category, setCategory] = useState('');
    const [price, setPrice] = useState('');
    const [imageUrl, setImageUrl] = useState('');

    // User blocking.
    const [userId, setUserId] = useState('');

    const refresh = () =>
        searchProducts('')
            .then((list) => setProducts(list.products))
            .catch(() => setError('Could not load products.'));

    const refreshOrders = () =>
        listAllOrders()
            .then(setOrders)
            .catch(() => setError('Could not load orders.'));

    useEffect(() => {
        if (getToken() === null) {
            router.replace('/login');
            return;
        }
        checkAdmin().then((ok) => {
            setAuthorized(ok);
            if (ok) {
                refresh();
                refreshOrders();
            }
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [router]);

    const onCreate = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        setNotice(null);
        try {
            await createProduct({
                productName: name,
                productCategory: category || undefined,
                priceJpy: Number(price),
                productImageUrl: imageUrl || undefined
            });
            setName('');
            setCategory('');
            setPrice('');
            setImageUrl('');
            setNotice('Product created.');
            await refresh();
        } catch {
            setError('Could not create the product.');
        }
    };

    const onDelete = async (id: number) => {
        setError(null);
        setNotice(null);
        try {
            await deleteProduct(id);
            setNotice(`Deleted product #${id}.`);
            await refresh();
        } catch (err: unknown) {
            const status =
                typeof err === 'object' && err !== null && 'response' in err
                    ? (err as { response?: { status?: number } }).response?.status
                    : undefined;
            setError(
                status === 409
                    ? `Product #${id} is referenced by orders/reviews and can't be deleted.`
                    : `Could not delete product #${id}.`
            );
        }
    };

    const onBlock = async (block: boolean) => {
        setError(null);
        setNotice(null);
        const id = Number(userId);
        if (!id) return;
        try {
            if (block) await blockUser(id);
            else await unblockUser(id);
            setNotice(`User #${id} ${block ? 'blocked' : 'unblocked'}.`);
        } catch {
            setError(`Could not update user #${id}.`);
        }
    };

    if (authorized === null) {
        return <div className='flex h-screen items-center justify-center text-gray-400'>Loading…</div>;
    }
    if (!authorized) {
        return (
            <div className='flex h-screen items-center justify-center text-red-500'>You don’t have admin access.</div>
        );
    }

    return (
        <div className='min-h-screen flex flex-col'>
            <Header />
            <div className='flex flex-col gap-6 p-6'>
                <h2>Admin</h2>
                {error && <p className='text-red-500 text-sm'>{error}</p>}
                {notice && <p className='text-green-600 text-sm'>{notice}</p>}

                <section className='flex flex-col gap-3'>
                    <h4 className='font-semibold'>Add a product</h4>
                    <form onSubmit={onCreate} className='flex flex-col gap-2'>
                        <InputText placeholder='Name' value={name} onChange={(e) => setName(e.target.value)} required />
                        <InputText
                            placeholder='Category (optional)'
                            value={category}
                            onChange={(e) => setCategory(e.target.value)}
                        />
                        <InputText
                            inputMode='numeric'
                            placeholder='Price (JPY)'
                            value={price}
                            onChange={(e) => setPrice(e.target.value)}
                            required
                        />
                        <InputText
                            placeholder='Image URL (optional)'
                            value={imageUrl}
                            onChange={(e) => setImageUrl(e.target.value)}
                        />
                        <Button type='submit'>Create product</Button>
                    </form>
                </section>

                <section className='flex flex-col gap-2'>
                    <h4 className='font-semibold'>Products ({products.length})</h4>
                    {products.map((p) => (
                        <div key={p.id} className='flex flex-row justify-between items-center border-b py-2'>
                            <span className='text-sm'>
                                #{p.id} {p.productName}
                                {p.productCategory ? ` · ${p.productCategory}` : ''} · {formatJpy(p.priceJpy)}
                            </span>
                            <button
                                onClick={() => onDelete(p.id)}
                                className='text-red-500 text-sm border border-red-400 rounded px-2 py-1'
                            >
                                Delete
                            </button>
                        </div>
                    ))}
                </section>

                <section className='flex flex-col gap-2'>
                    <h4 className='font-semibold'>Orders ({orders.length})</h4>
                    {orders.length === 0 && <p className='text-sm text-gray-400'>No orders yet.</p>}
                    {orders.map((order) => (
                        <AdminOrderRow key={order.id} order={order} onChanged={refreshOrders} />
                    ))}
                </section>

                <section className='flex flex-col gap-2'>
                    <h4 className='font-semibold'>Block / unblock a user</h4>
                    <div className='flex flex-row gap-2 items-center'>
                        <InputText
                            inputMode='numeric'
                            placeholder='User id'
                            value={userId}
                            onChange={(e) => setUserId(e.target.value)}
                        />
                        <button
                            onClick={() => onBlock(true)}
                            className='text-red-500 text-sm border border-red-400 rounded px-3 py-2 whitespace-nowrap'
                        >
                            Block
                        </button>
                        <button
                            onClick={() => onBlock(false)}
                            className='text-primary text-sm border border-primary rounded px-3 py-2 whitespace-nowrap'
                        >
                            Unblock
                        </button>
                    </div>
                </section>
            </div>
            <Footer />
        </div>
    );
}
