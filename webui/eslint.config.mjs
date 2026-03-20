import typescriptEslint from "@typescript-eslint/eslint-plugin";
import react from "eslint-plugin-react";
import tsParser from "@typescript-eslint/parser";
import stylistic from '@stylistic/eslint-plugin'
import path from "node:path";
import { fileURLToPath } from "node:url";
import js from "@eslint/js";
import { FlatCompat } from "@eslint/eslintrc";
import { reactRefresh } from "eslint-plugin-react-refresh";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const compat = new FlatCompat({
    baseDirectory: __dirname,
    recommendedConfig: js.configs.recommended,
    allConfig: js.configs.all
});

export default [...compat.extends(
    "eslint:recommended",
    "plugin:@typescript-eslint/eslint-recommended",
    "plugin:@typescript-eslint/recommended",
    "plugin:react/recommended",
),
    reactRefresh.configs.vite(),
{
    files: ["**/*.ts", "**/*.tsx"],
    plugins: {
        "@typescript-eslint": typescriptEslint,
        "@stylistic": stylistic,
        react,
    },

    languageOptions: {
        parser: tsParser,
    },

    settings: {
        react: {
            version: "detect",
        },
    },

    rules: {
        "@typescript-eslint/ban-types": "off",
        "@/brace-style": ["warn", "1tbs"],

        "@/comma-spacing": ["warn", {
            before: false,
            after: true,
        }],

        "@/func-call-spacing": ["warn", "never"],

        "@/keyword-spacing": ["warn", {
            before: true,
            after: true,
        }],

        "@typescript-eslint/no-empty-function": "off",
        "@typescript-eslint/no-empty-interface": "off",
        "@typescript-eslint/no-explicit-any": "off",
        "@typescript-eslint/no-inferrable-types": "off",
        "@typescript-eslint/no-namespace": "off",
        "@typescript-eslint/no-non-null-assertion": "off",
        "@typescript-eslint/no-unused-vars": "off",
        "@typescript-eslint/no-var-requires": "off",
        "@/semi": ["error", "always"],
        "@stylistic/type-annotation-spacing": "warn",
        "react/prop-types": "off",
        "react/react-in-jsx-scope": ["off"],
        "array-bracket-spacing": ["warn", "never"],

        "arrow-spacing": ["warn", {
            before: true,
            after: true,
        }],

        "computed-property-spacing": ["warn", "never"],
        "jsx-quotes": ["error", "prefer-single"],

        "key-spacing": ["warn", {
            beforeColon: false,
            afterColon: true,
        }],

        "linebreak-style": ["warn", "unix"],
        "new-parens": "error",
        "no-trailing-spaces": "warn",
        "no-whitespace-before-property": "warn",
        "object-curly-spacing": ["warn", "always"],

        "semi-spacing": ["warn", {
            before: false,
            after: true,
        }],

        "space-before-blocks": ["warn", "always"],
        "space-in-parens": ["warn", "never"],
        "space-infix-ops": "warn",
        "space-unary-ops": "warn",

        "switch-colon-spacing": ["warn", {
            before: false,
            after: true,
        }],
    },
}];