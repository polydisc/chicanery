/** @type {import('next').NextConfig} */
const nextConfig = {
    images: {
        // Product image URLs come from the backend and can point at arbitrary
        // hosts, so skip the Image Optimization domain allowlist.
        unoptimized: true
    }
};

export default nextConfig;
