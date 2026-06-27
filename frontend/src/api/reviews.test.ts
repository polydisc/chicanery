import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '@/test/msw/server';
import { createReview, getReviews } from './reviews';

describe('reviews api', () => {
    it('getReviews returns the reviews for a product', async () => {
        server.use(http.get('*/api/v1/reviews/5', () => HttpResponse.json([{ content: 'Great', rating: 5 }])));
        const reviews = await getReviews(5);
        expect(reviews).toEqual([{ content: 'Great', rating: 5 }]);
    });

    it('createReview POSTs the body and returns the created review', async () => {
        let posted: unknown = null;
        server.use(
            http.post('*/api/v1/reviews/5', async ({ request }) => {
                posted = await request.json();
                return HttpResponse.json(
                    { content: 'Nice', rating: 4 },
                    {
                        status: 201
                    }
                );
            })
        );
        const created = await createReview(5, { content: 'Nice', rating: 4 });
        expect(posted).toEqual({ content: 'Nice', rating: 4 });
        expect(created).toEqual({ content: 'Nice', rating: 4 });
    });
});
