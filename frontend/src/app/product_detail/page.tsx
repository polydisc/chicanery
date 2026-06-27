'use client';

import Image from 'next/image';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { getProduct } from '@/api/products';
import { createReview, getReviews } from '@/api/reviews';
import { addItem, ensureCart } from '@/api/cart';
import { getToken } from '@/api/token';
import type { Product, Review } from '@/api/types';
import { formatJpy } from '@/lib/format';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import Button from '@/components/ui/Button';

const FALLBACK_IMAGE = '/combo_examples/breakfast-quinoa-and-red-fruit-salad-134061-1-removebg-preview 2.png';

function StarPicker({ value, onChange }: { value: number; onChange: (n: number) => void }): JSX.Element {
    return (
        <div className='flex flex-row'>
            {[1, 2, 3, 4, 5].map((n) => (
                <button
                    key={n}
                    type='button'
                    aria-label={`${n} star${n > 1 ? 's' : ''}`}
                    className={n <= value ? 'text-primary' : 'text-gray-300'}
                    onClick={() => onChange(n)}
                >
                    ★
                </button>
            ))}
        </div>
    );
}

function ProductDetail(): JSX.Element {
    const router = useRouter();
    const searchParams = useSearchParams();
    const id = Number(searchParams.get('id'));

    const [product, setProduct] = useState<Product | null>(null);
    const [reviews, setReviews] = useState<Review[]>([]);
    const [quantity, setQuantity] = useState(1);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [adding, setAdding] = useState(false);

    // Review form state.
    const [authed, setAuthed] = useState(false);
    const [rating, setRating] = useState(5);
    const [content, setContent] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [reviewError, setReviewError] = useState<string | null>(null);

    useEffect(() => {
        setAuthed(getToken() !== null);
    }, []);

    useEffect(() => {
        if (!id || Number.isNaN(id)) {
            setError('Unknown product.');
            setLoading(false);
            return;
        }
        let cancelled = false;
        setLoading(true);
        Promise.all([getProduct(id), getReviews(id).catch(() => [])])
            .then(([p, r]) => {
                if (cancelled) return;
                setProduct(p);
                setReviews(r);
            })
            .catch(() => {
                if (!cancelled) setError('Could not load this product.');
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [id]);

    const onAddToCart = async () => {
        if (!product) return;
        if (getToken() === null) {
            router.push('/login');
            return;
        }
        setAdding(true);
        try {
            const cartId = await ensureCart();
            await addItem(cartId, { productId: product.id, quantity });
            router.push('/basket');
        } catch {
            setError('Could not add to basket.');
        } finally {
            setAdding(false);
        }
    };

    const onSubmitReview = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!product) return;
        setReviewError(null);
        setSubmitting(true);
        try {
            await createReview(product.id, { content, rating });
            setContent('');
            setRating(5);
            const fresh = await getReviews(product.id);
            setReviews(fresh);
        } catch {
            setReviewError('Could not submit your review.');
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return <div className='flex h-screen items-center justify-center text-gray-400'>Loading…</div>;
    }
    if (error || !product) {
        return (
            <div className='flex h-screen items-center justify-center text-red-500'>
                {error ?? 'Product not found.'}
            </div>
        );
    }

    return (
        <div className='flex flex-col min-h-screen'>
            <Header />
            <div className='bg-primary flex h-64 justify-center items-center'>
                <Image
                    src={product.productImageUrl || FALLBACK_IMAGE}
                    alt={product.productName}
                    width={128}
                    height={128}
                    className='rounded-box'
                />
            </div>
            <div className='bg-white p-10 text-left flex-1'>
                <div className='flex flex-col gap-3'>
                    <div className='flex flex-row justify-between items-center'>
                        <h2>{product.productName}</h2>
                        <span className='text-lg font-semibold'>{formatJpy(product.priceJpy)}</span>
                    </div>
                    {product.productCategory && <p className='text-gray-400 text-sm'>{product.productCategory}</p>}

                    <div className='flex flex-row items-center gap-4'>
                        <span className='text-sm text-gray-500'>Quantity</span>
                        <div className='flex flex-row items-center gap-3'>
                            <button
                                className='btn btn-circle btn-sm'
                                aria-label='Decrease quantity'
                                onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                            >
                                −
                            </button>
                            <span>{quantity}</span>
                            <button
                                className='btn btn-circle btn-sm'
                                aria-label='Increase quantity'
                                onClick={() => setQuantity((q) => q + 1)}
                            >
                                +
                            </button>
                        </div>
                    </div>

                    <div className='flex flex-col gap-1'>
                        <h4 className='text-sm font-semibold'>Reviews</h4>
                        {reviews.length === 0 && (
                            <p className='text-sm text-gray-400'>No reviews yet — be the first.</p>
                        )}
                        {reviews.map((review, index) => (
                            <div key={index} className='text-sm text-gray-500'>
                                <span className='text-primary'>{'★'.repeat(review.rating)}</span> {review.content}
                            </div>
                        ))}
                    </div>

                    {authed ? (
                        <form onSubmit={onSubmitReview} className='flex flex-col gap-2 border-t pt-3'>
                            <span className='text-sm font-semibold'>Write a review</span>
                            <StarPicker value={rating} onChange={setRating} />
                            <textarea
                                className='p-2 rounded-lg border text-black text-sm'
                                rows={2}
                                placeholder='Share what you thought…'
                                value={content}
                                onChange={(e) => setContent(e.target.value)}
                                required
                            />
                            {reviewError && <p className='text-red-500 text-sm'>{reviewError}</p>}
                            <button
                                type='submit'
                                disabled={submitting || content.trim() === ''}
                                className='rounded-lg bg-primary py-2 text-white text-sm disabled:opacity-50'
                            >
                                {submitting ? 'Submitting…' : 'Submit review'}
                            </button>
                        </form>
                    ) : (
                        <p className='text-sm text-gray-400 border-t pt-3'>
                            <Link href='/login' className='text-primary'>
                                Log in
                            </Link>{' '}
                            to write a review.
                        </p>
                    )}

                    <Button onClick={onAddToCart} disabled={adding}>
                        {adding ? 'Adding…' : 'Add to basket'}
                    </Button>
                </div>
            </div>
            <Footer />
        </div>
    );
}

export default function ProductDetailPage(): JSX.Element {
    return (
        <Suspense fallback={null}>
            <ProductDetail />
        </Suspense>
    );
}
