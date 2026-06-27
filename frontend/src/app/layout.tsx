import type { Metadata } from 'next';
import { Cabin, Inter } from 'next/font/google';
import './globals.css';

const cabin = Cabin({ subsets: ['latin'] });

export const metadata: Metadata = {
    title: 'Chicanery',
    description: 'A small online shopping site — browse, review, cart, pay, and track orders.'
};

/**
 * Switch unauthenticated layout vs authenticated layout.
 */
export default function RootLayout({
    children
}: Readonly<{
    children: React.ReactNode;
}>) {
    return (
        <html lang='en'>
            <body className={cabin.className}>{children}</body>
        </html>
    );
}
