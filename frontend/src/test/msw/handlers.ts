import { http, HttpResponse } from 'msw';

const API = '*/api/v1';

// Default happy-path handlers. Tests override these per-case with `server.use`.
export const handlers = [
    http.get(`${API}/products/search`, () =>
        HttpResponse.json({
            products: [
                {
                    id: 1,
                    productName: 'Banana',
                    productCategory: 'fruit',
                    priceJpy: 200
                }
            ],
            pageNumber: 0
        })
    ),
    http.get(`${API}/products/categories`, () => HttpResponse.json(['combo', 'fruit']))
];
