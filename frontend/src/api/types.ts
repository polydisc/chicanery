import type { components } from './schema';

type Schemas = components['schemas'];

export type Product = Schemas['Product'];
export type ProductList = Schemas['ProductList'];
export type Review = Schemas['Review'];
export type CreateReview = Schemas['CreateReview'];
export type Cart = Schemas['Cart'];
export type CartItem = Schemas['CartItem'];
export type AddCartItem = Schemas['AddCartItem'];
export type UpdateCartItem = Schemas['UpdateCartItem'];
export type Order = Schemas['Order'];
export type OrderItem = Schemas['OrderItem'];
export type CreateOrder = Schemas['CreateOrder'];
export type PaymentRequest = Schemas['PaymentRequest'];
export type ShipOrder = Schemas['ShipOrder'];
export type Notification = Schemas['Notification'];
export type LoginRequest = Schemas['LoginRequest'];
export type RegisterRequest = Schemas['RegisterRequest'];
