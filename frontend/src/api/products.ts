import { apiClient } from './client';
import type { Product, ProductList } from './types';

export async function searchProducts(query = '', category?: string, page = 0): Promise<ProductList> {
    const { data } = await apiClient.get<ProductList>('/products/search', {
        params: { query: query || undefined, category: category || undefined, page }
    });
    return data;
}

export async function getProduct(id: number): Promise<Product> {
    const { data } = await apiClient.get<Product>(`/products/${id}`);
    return data;
}

export async function listCategories(): Promise<string[]> {
    const { data } = await apiClient.get<string[]>('/products/categories');
    return data;
}
