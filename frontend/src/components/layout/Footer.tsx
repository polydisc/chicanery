import { brandFont } from '@/lib/fonts';

// Compact centered footer shown on the authenticated pages. `mt-auto` keeps it at
// the bottom on short pages (the page roots are min-h-screen flex-col).
export default function Footer(): JSX.Element {
    return (
        <footer className='mt-auto border-t border-gray-100 bg-white py-8'>
            <div className='flex flex-col items-center gap-2 px-4 text-center'>
                <span className={`${brandFont.className} text-xl text-brand`}>Chicanery</span>
                <p className='text-xs text-gray-400'>
                    A portfolio project ·{' '}
                    <a
                        href='https://github.com/polydisc/chicanery'
                        target='_blank'
                        rel='noreferrer'
                        className='hover:text-primary'
                    >
                        GitHub
                    </a>{' '}
                    ·{' '}
                    <a
                        href='https://igarash1.github.io'
                        target='_blank'
                        rel='noreferrer'
                        className='hover:text-primary'
                    >
                        igarash1.github.io
                    </a>{' '}
                    · © 2026
                </p>
            </div>
        </footer>
    );
}
