import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '@/test/msw/server';
import { listCategories, searchProducts } from './products';

describe('products api', () => {
    it('searchProducts returns the product list (default handler)', async () => {
        const list = await searchProducts('banana');
        expect(list.products).toHaveLength(1);
        expect(list.products[0]?.productName).toBe('Banana');
    });

    it('searchProducts forwards the category filter as a query param', async () => {
        let seenCategory: string | null = null;
        server.use(
            http.get('*/api/v1/products/search', ({ request }) => {
                seenCategory = new URL(request.url).searchParams.get('category');
                return HttpResponse.json({ products: [], pageNumber: 0 });
            })
        );
        await searchProducts('', 'combo');
        expect(seenCategory).toBe('combo');
    });

    it('listCategories returns the distinct categories', async () => {
        expect(await listCategories()).toEqual(['combo', 'fruit']);
    });
});
