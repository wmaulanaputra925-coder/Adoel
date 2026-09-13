/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

// Netlify melayani situs dari root domain (mis. https://nama-situs.netlify.app/),
// bukan dari subpath repo seperti GitHub Pages — base "/" sudah benar untuk itu.
export default defineConfig({
  base: "/",
  server: {
    host: "0.0.0.0",
    port: 3000,
    allowedHosts: true,
  },
  // web/dist, not "../dist" — netlify.toml's `base = "web"` resolves `publish = "dist"`
  // relative to that base directory, and the root .gitignore already expects web/dist.
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      injectRegister: "auto",
      includeAssets: [
        "favicon.svg",
        "apple-touch-icon.png",
        "apple-touch-icon-precomposed.png",
        "icons/icon-192.png",
        "icons/icon-512.png",
        "icons/apple-touch-icon.png",
      ],
      manifest: {
        name: "Adoel — Jadwal Doffing",
        short_name: "Adoel",
        description: "Pencatat jadwal doffing mesin Water Jet Loom",
        theme_color: "#09090b",
        background_color: "#09090b",
        display: "standalone",
        start_url: "/",
        scope: "/",
        icons: [
          { src: "icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
          { src: "icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "maskable" },
          { src: "icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
          { src: "icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
        ],
      },
      workbox: {
        globPatterns: ["**/*.{js,css,html,svg,png,ico,woff,woff2}"],
        cleanupOutdatedCaches: true,
        clientsClaim: true,
        skipWaiting: true,
        navigateFallback: "index.html",
      },
    }),
  ],
  // Test domain murni (parsing/rumus/format/serialisasi) — tidak butuh DOM, jadi environment
  // "node". File test dikecualikan dari tsconfig.app.json supaya `tsc -b` (build) tidak ikut
  // mengecek-tipe mereka; vitest yang meng-handle transpilasi + tipe test.
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
});
