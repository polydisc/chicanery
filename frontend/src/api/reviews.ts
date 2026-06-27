import { apiClient } from './client';
import type { CreateReview, Review } from './types';

export async function getReviews(productId: number): Promise<Review[]> {
    const { data } = await apiClient.get<Review[]>(`/reviews/${productId}`);
    return data;
}

export async function createReview(productId: number, input: CreateReview): Promise<Review> {
    const { data } = await apiClient.post<Review>(`/reviews/${productId}`, input);
    return data;
}
