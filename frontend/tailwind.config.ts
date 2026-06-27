import type { Config } from 'tailwindcss';
import daisyui from 'daisyui';

const config: Config = {
    content: [
        './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
        './src/components/**/*.{js,ts,jsx,tsx,mdx}',
        './src/app/**/*.{js,ts,jsx,tsx,mdx}'
    ],
    theme: {
        extend: {
            colors: {
                // The brand accent. Defined here (not only as a backgroundColor)
                // so `text-primary` / `border-primary` resolve to the orange too
                // — otherwise they fell through to DaisyUI's default theme
                // primary (a blue), which is why stray blue text appeared.
                primary: '#FFA451',
                // Deep terracotta used for the brand wordmark (a grounded,
                // higher-contrast partner to the bright #FFA451 accent).
                brand: '#C2410C'
            },
            backgroundImage: {
                'gradient-radial': 'radial-gradient(var(--tw-gradient-stops))',
                'gradient-conic': 'conic-gradient(from 180deg at 50% 50%, var(--tw-gradient-stops))'
            },
            backgroundColor: (theme) => ({
                primary: '#FFA451',
                secondary: '#ffffff',
                danger: '#e3342f'
            })
        }
    },
    plugins: [daisyui]
};
export default config;
