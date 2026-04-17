import path from 'node:path';
import react from '@vitejs/plugin-react';
import { defineConfig, PluginOption } from 'vite';
import { visualizer } from 'rollup-plugin-visualizer';

const outRootDir = path.join(__dirname, "dist");

export default defineConfig(() => ({
    plugins: [react(), visualizer() as PluginOption],
    server: {
        host: true,
        port: 3000,
    },
    preview: {
        port: 3000,
    },
    resolve: {
        alias: {
            '@': path.resolve(__dirname, './src')
        }
    },
    publicDir: 'static',
    build: {
        target: 'es2020',
        minify: true,
        sourcemap: true,
        outDir: outRootDir,
        emptyOutDir: true,
        rollupOptions: {
            output: {
                entryFileNames: 'bundle-[hash].js',
                assetFileNames: '[name]-[hash][extname]',
                chunkFileNames: 'chunk-[name]-[hash].js',
            }
        }
    },
}));
