'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { checkAdmin } from '@/api/admin';
import { listNotifications, unreadCount } from '@/api/notifications';
import { getToken } from '@/api/token';
import { brandFont } from '@/lib/fonts';
import { useAuth } from '@/hooks/useAuth';

// Persistent top navigation for authenticated pages. The "Chicanery" brand
// links home; the notification bell (with unread badge) plus Orders / Basket,
// the Admin link for admins, and Log out follow. The current page is marked with
// an orange background (white text), not a coloured-text link.
export default function Header(): JSX.Element {
    const { logout } = useAuth();
    const pathname = usePathname();
    const [isAdmin, setIsAdmin] = useState(false);
    const [unread, setUnread] = useState(0);

    useEffect(() => {
        if (getToken() === null) return;
        checkAdmin()
            .then(setIsAdmin)
            .catch(() => setIsAdmin(false));
        listNotifications()
            .then((n) => setUnread(unreadCount(n)))
            .catch(() => setUnread(0));
    }, [pathname]);

    const item = (href: string) =>
        `rounded px-2.5 py-1 text-sm ${
            pathname === href ? 'bg-primary text-white' : 'text-gray-600 hover:bg-gray-100'
        }`;

    return (
        <header className='sticky top-0 z-20 border-b border-gray-100 bg-white'>
            <nav className='flex flex-row items-center gap-3 px-4 py-3'>
                <Link href='/home' className={`${brandFont.className} text-2xl leading-none text-brand`}>
                    Chicanery
                </Link>
                <div className='ml-auto flex flex-row items-center gap-3'>
                    <Link
                        href='/notifications'
                        aria-label='Notifications'
                        className={`relative rounded p-1.5 ${
                            pathname === '/notifications' ? 'bg-primary text-white' : 'text-gray-600 hover:bg-gray-100'
                        }`}
                    >
                        <svg
                            xmlns='http://www.w3.org/2000/svg'
                            fill='none'
                            viewBox='0 0 24 24'
                            strokeWidth={1.5}
                            stroke='currentColor'
                            className='h-5 w-5'
                        >
                            <path
                                strokeLinecap='round'
                                strokeLinejoin='round'
                                d='M14.857 17.082a23.848 23.848 0 0 0 5.454-1.31A8.967 8.967 0 0 1 18 9.75V9A6 6 0 0 0 6 9v.75a8.967 8.967 0 0 1-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 0 1-5.714 0m5.714 0a3 3 0 1 1-5.714 0'
                            />
                        </svg>
                        {unread > 0 && (
                            <span className='absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] text-white ring-2 ring-white'>
                                {unread > 9 ? '9+' : unread}
                            </span>
                        )}
                    </Link>
                    <Link href='/track_order' className={item('/track_order')}>
                        Orders
                    </Link>
                    <Link href='/basket' className={item('/basket')}>
                        Basket
                    </Link>
                    {isAdmin && (
                        <Link href='/admin' className={item('/admin')}>
                            Admin
                        </Link>
                    )}
                    <button onClick={logout} className='rounded px-2.5 py-1 text-sm text-gray-600 hover:bg-gray-100'>
                        Log out
                    </button>
                </div>
            </nav>
        </header>
    );
}
