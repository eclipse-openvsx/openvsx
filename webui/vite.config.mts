/// <reference types="vitest/config" />
import path from 'node:path';
import react from '@vitejs/plugin-react';
import { defineConfig, PluginOption } from 'vite';
import { visualizer } from 'rollup-plugin-visualizer';

const outRootDir = path.join(import.meta.dirname, 'dist');

export default defineConfig(() => ({
    plugins: [react(), visualizer() as PluginOption],
    server: {
        host: true,
        port: 3000
    },
    preview: {
        port: 3000
    },
    resolve: {
        alias: {
            '@': path.resolve(import.meta.dirname, './src')
        }
    },
    publicDir: 'static',
    test: {
        include: ['test/unit/**/*.spec.{ts,tsx}'],
        environment: 'jsdom',
        setupFiles: ['./test/setup.ts'],
        // Give userEvent/waitFor-heavy tests more room than the 5s default on a loaded machine.
        testTimeout: 15000,
        // Spawning a fork per CPU all at once (each booting jsdom + React) can itself blow past
        // vitest's own worker-startup timeout under load; capping concurrency avoids that pileup.
        maxWorkers: '50%',
        server: {
            deps: {
                // their ESM builds import directory paths Node's resolver rejects; let vite bundle them
                inline: ['@mui/x-charts', '@mui/x-data-grid', '@mui/x-date-pickers']
            }
        }
    },
    // lightningcss (Vite 8's default CSS transformer) ships no prebuilt binary for ppc64le,
    // and its minifier isn't needed once postcss is handling transforms - see
    // https://github.com/eclipse-openvsx/openvsx/issues/2051
    css: {
        transformer: 'postcss'
    },
    build: {
        target: 'es2020',
        minify: true,
        cssMinify: false,
        sourcemap: true,
        outDir: outRootDir,
        emptyOutDir: true,
        chunkSizeWarningLimit: 800,
        rollupOptions: {
            output: {
                entryFileNames: 'bundle-[hash].js',
                assetFileNames: '[name]-[hash][extname]',
                chunkFileNames: 'chunk-[name]-[hash].js'
            }
        }
    }
}));
