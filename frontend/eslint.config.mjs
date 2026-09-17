import { dirname } from "path";
import { fileURLToPath } from "url";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
  baseDirectory: __dirname,
});

const eslintConfig = [
  ...compat.extends("next/core-web-vitals", "next/typescript"),
  {
    // .next-dev is the dev server's output folder (see next.config.ts); like .next it is
    // generated code and must not be linted.
    ignores: ["node_modules/**", ".next/**", ".next-dev/**", "out/**", "build/**", "next-env.d.ts"],
  },
];

export default eslintConfig;
