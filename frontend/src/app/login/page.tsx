'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { login } from '@/api/auth';
import { getToken } from '@/api/token';
import Button from '@/components/ui/Button';
import InputText from '@/components/ui/InputText';

export default function Login(): JSX.Element {
    const router = useRouter();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    // Already signed in? Skip the form.
    useEffect(() => {
        if (getToken() !== null) router.replace('/home');
    }, [router]);

    const onSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        setSubmitting(true);
        try {
            await login({ username, password });
            router.push('/home');
        } catch {
            setError('Invalid email or password.');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className='flex flex-col h-screen justify-between'>
            <div className='bg-primary flex flex-1 justify-center items-center'>
                <h2 className='text-white'>Welcome back</h2>
            </div>
            <form onSubmit={onSubmit} className='bg-white p-10 text-left'>
                <h3 className='pb-4'>Log in to your account</h3>
                <div className='py-2'>
                    <InputText
                        type='email'
                        inputMode='email'
                        placeholder='you@example.com'
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                    />
                </div>
                <div className='py-2'>
                    <input
                        type='password'
                        placeholder='Password'
                        className='p-3 rounded-lg w-full text-black'
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                    />
                </div>
                {error && <p className='text-red-500 text-sm py-2'>{error}</p>}
                <div className='py-3'>
                    <Button type='submit' disabled={submitting}>
                        {submitting ? 'Logging in…' : 'Log in'}
                    </Button>
                </div>
                <p className='text-sm text-gray-400 text-center'>
                    No account?{' '}
                    <Link href='/authentication' className='text-primary'>
                        Create one
                    </Link>
                </p>
            </form>
        </div>
    );
}
