'use client';

import Image from 'next/image';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { listCategories, searchProducts } from '@/api/products';
import type { Product } from '@/api/types';
import { formatJpy } from '@/lib/format';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import SearchInput from '@/components/ui/SearchInput';

const FALLBACK_IMAGE = '/combo_examples/Glowing-Berry-Fruit-Salad-8-720x720-removebg-preview 1.png';

// Fixed-size card so the combo list stays aligned regardless of image aspect
// ratio or title length: a square image box (object-contain) plus a two-line
// title area, so titles and prices line up across cards.
function ProductCard({ product }: { product: Product }): JSX.Element {
    return (
        <Link href={`/product_detail?id=${product.id}`} className='carousel-item shrink-0'>
            <div className='flex w-40 flex-col gap-1'>
                <div className='relative h-40 w-40 overflow-hidden rounded-box bg-gray-50'>
                    <Image
                        src={product.productImageUrl || FALLBACK_IMAGE}
                        alt={product.productName}
                        fill
                        sizes='160px'
                        className='object-contain p-2'
                    />
                </div>
                <div className='line-clamp-2 min-h-[2.5rem] text-sm font-medium'>{product.productName}</div>
                <div className='text-sm text-gray-600'>{formatJpy(product.priceJpy)}</div>
            </div>
        </Link>
    );
}

function CategoryChips({
    categories,
    selected,
    onSelect
}: {
    categories: string[];
    selected: string | null;
    onSelect: (c: string | null) => void;
}): JSX.Element {
    const chip = (active: boolean) =>
        `px-3 py-1 rounded-full text-sm whitespace-nowrap ${
            active ? 'bg-primary text-white' : 'bg-gray-100 text-gray-600'
        }`;
    return (
        <div className='flex flex-row gap-2 overflow-x-auto py-2'>
            <button className={chip(selected === null)} onClick={() => onSelect(null)}>
                All
            </button>
            {categories.map((c) => (
                <button key={c} className={chip(selected === c)} onClick={() => onSelect(c)}>
                    {c}
                </button>
            ))}
        </div>
    );
}

function HomeContent(): JSX.Element {
    const searchParams = useSearchParams();
    const [query, setQuery] = useState('');
    const [category, setCategory] = useState<string | null>(searchParams.get('category'));
    const [categories, setCategories] = useState<string[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        listCategories()
            .then(setCategories)
            .catch(() => setCategories([]));
    }, []);

    // Debounced search: refetch when the query or selected category settles.
    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(null);
        const handle = setTimeout(() => {
            searchProducts(query, category ?? undefined)
                .then((list) => {
                    if (!cancelled) setProducts(list.products);
                })
                .catch(() => {
                    if (!cancelled) setError('Could not load products.');
                })
                .finally(() => {
                    if (!cancelled) setLoading(false);
                });
        }, 300);
        return () => {
            cancelled = true;
            clearTimeout(handle);
        };
    }, [query, category]);

    return (
        <div className='min-h-screen flex flex-col'>
            <Header />
            <div className='flex flex-col justify-between p-4'>
                <div className='flex-1'>
                    <div className='text-xl font-thin w-64'>
                        <b>What fruit salad combo do you want today?</b>
                    </div>
                </div>
                <div className='bg-secondary flex flex-row mt-6 flex-1 justify-center items-center'>
                    <div className='flex-1 text-xs'>
                        <SearchInput
                            placeholder='Search by name or category'
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                        />
                    </div>
                </div>
                {categories.length > 0 && (
                    <CategoryChips categories={categories} selected={category} onSelect={setCategory} />
                )}
            </div>
            <div className='flex-col p-4 w-full'>
                <div className='text-2xl'>{category ? `${category} combos` : 'Recommended Combo'}</div>
                {loading && <p className='py-4 text-gray-400'>Loading…</p>}
                {error && <p className='py-4 text-red-500'>{error}</p>}
                {!loading && !error && products.length === 0 && <p className='py-4 text-gray-400'>No combos found.</p>}
                <div className='carousel rounded-box space-x-4'>
                    {products.map((product) => (
                        <ProductCard key={product.id} product={product} />
                    ))}
                </div>
            </div>
            <Footer />
        </div>
    );
}

export default function Home(): JSX.Element {
    return (
        <Suspense fallback={null}>
            <HomeContent />
        </Suspense>
    );
}
