import { apiClient } from './client';
import { setToken } from './token';
import type { LoginRequest, RegisterRequest } from './types';

// login/register return the raw JWT as a JSON string body.
export async function login(req: LoginRequest): Promise<string> {
    const { data } = await apiClient.post<string>('/login', req);
    setToken(data);
    return data;
}

export async function register(req: RegisterRequest): Promise<string> {
    const { data } = await apiClient.post<string>('/register', req);
    setToken(data);
    return data;
}
