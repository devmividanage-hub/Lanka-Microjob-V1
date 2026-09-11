import { defineConfig } from 'vite';

/**
 * Dev-server configuration.
 *
 * index.html is a plain static page, so Vite is used as a dev server and bundler only.
 * The API paths are proxied to the gateway, which keeps browser code on a single origin
 * (no CORS) and identical to how nginx behaves in the container image.
 */
const gateway = process.env.VITE_GATEWAY_URL || 'http://localhost:9000';

export default defineConfig({
    server: {
        host: '0.0.0.0',
        port: Number(process.env.VITE_DEV_PORT || 3000),
        proxy: Object.fromEntries(
            ['/auth', '/jobs', '/applications', '/matches', '/brokers', '/notifications', '/api-docs']
                .map(path => [path, { target: gateway, changeOrigin: true }]),
        ),
    },
    preview: { host: '0.0.0.0', port: 4173 },
    build: { outDir: 'dist', sourcemap: false },
});
