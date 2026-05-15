/// <reference types="vite/client" />

/**
 * Type declarations for Vue single-file components (.vue files)
 * Allows TypeScript to recognize .vue files and properly infer their types
 * Prevents "module not found" errors when importing Vue components in TypeScript
 */
declare module "*.vue" {
  import type { DefineComponent } from "vue";
  const component: DefineComponent<{}, {}, any>;
  export default component;
}
