'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { getToken } from '@/api/token';

// Entry point: send visitors into the app. Signed-in users go straight to the
// catalog; everyone else starts at the welcome/onboarding screen.
export default function Index(): JSX.Element {
    const router = useRouter();

    useEffect(() => {
        router.replace(getToken() !== null ? '/home' : '/welcome');
    }, [router]);

    return (
        <div className='flex h-screen items-center justify-center text-gray-400'>
            Loading…
        </div>
    );
}
