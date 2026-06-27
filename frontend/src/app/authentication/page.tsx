'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { motion, Variants } from 'framer-motion';
import KissingFruitBasketClipArt from '../../components/ui/icon/authentication/KissingFruitBasketClip';
import FruitDrop2 from '../../components/ui/icon/welcome/FruitDrop2';
import Ellipse1 from '../../components/ui/icon/welcome/Ellipse1';
import InputText from '../../components/ui/InputText';
import Button from '@/components/ui/Button';
import { register } from '@/api/auth';
import { getToken } from '@/api/token';

export default function Register(): JSX.Element {
    const router = useRouter();

    // Already signed in? Skip the form.
    useEffect(() => {
        if (getToken() !== null) router.replace('/home');
    }, [router]);

    const [form, setForm] = useState({
        emailAddress: '',
        password: '',
        firstName: '',
        lastName: ''
    });
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    const update = (field: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
        setForm((prev) => ({ ...prev, [field]: e.target.value }));

    const onSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        setSubmitting(true);
        try {
            await register(form);
            router.push('/home');
        } catch (err: unknown) {
            const status =
                typeof err === 'object' && err !== null && 'response' in err
                    ? (err as { response?: { status?: number } }).response?.status
                    : undefined;
            setError(
                status === 409
                    ? 'That email is already registered.'
                    : 'Could not create your account. Please check your details.'
            );
        } finally {
            setSubmitting(false);
        }
    };

    const parentVariant: Variants = {
        initial: { opacity: 0 },
        animate: { opacity: 1, transition: { staggerChildren: 0.5 } }
    };
    const childVariant: Variants = {
        initial: { opacity: 0, scale: 0.5 },
        animate: { opacity: 1, scale: 1, transition: { duration: 0.5 } }
    };
    const textParentVariant: Variants = {
        initial: { opacity: 0 },
        animate: { opacity: 1, transition: { staggerChildren: 0.2 } }
    };
    const textChildVariant: Variants = {
        initial: { opacity: 0, y: 50 },
        animate: { opacity: 1, y: 0 }
    };

    return (
        <motion.div
            initial='initial'
            animate='animate'
            variants={parentVariant}
            className='flex flex-col h-screen justify-between'
        >
            <motion.div className='bg-primary flex flex-1 justify-center items-center'>
                <motion.div
                    initial='initial'
                    animate='animate'
                    variants={parentVariant}
                    className='flex flex-col items-end'
                >
                    <motion.div variants={childVariant}>
                        <FruitDrop2 />
                    </motion.div>
                    <motion.div variants={childVariant}>
                        <div className='relative'>
                            <KissingFruitBasketClipArt />
                        </div>
                        <div className='relative pt-2'>
                            <Ellipse1 />
                        </div>
                    </motion.div>
                </motion.div>
            </motion.div>
            <motion.form onSubmit={onSubmit} variants={textParentVariant} className='bg-white p-10 text-left'>
                <motion.div variants={textChildVariant} className='w-full'>
                    <h3>Create your account</h3>
                </motion.div>
                <motion.div variants={textChildVariant} className='py-2'>
                    <InputText
                        type='email'
                        inputMode='email'
                        placeholder='you@example.com'
                        value={form.emailAddress}
                        onChange={update('emailAddress')}
                    />
                </motion.div>
                <motion.div variants={textChildVariant} className='py-2'>
                    <input
                        type='password'
                        placeholder='Password'
                        className='p-3 rounded-lg w-full text-black'
                        value={form.password}
                        onChange={update('password')}
                    />
                </motion.div>
                <motion.div variants={textChildVariant} className='py-2 flex gap-2'>
                    <InputText placeholder='First name' value={form.firstName} onChange={update('firstName')} />
                    <InputText placeholder='Last name' value={form.lastName} onChange={update('lastName')} />
                </motion.div>
                {error && <p className='text-red-500 text-sm py-2'>{error}</p>}
                <motion.div variants={textChildVariant} className='py-2'>
                    <Button type='submit' disabled={submitting}>
                        {submitting ? 'Creating…' : 'Start Ordering'}
                    </Button>
                </motion.div>
                <p className='text-sm text-gray-400 text-center'>
                    Already have an account?{' '}
                    <Link href='/login' className='text-primary'>
                        Log in
                    </Link>
                </p>
            </motion.form>
        </motion.div>
    );
}
