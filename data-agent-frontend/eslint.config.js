/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import vuePlugin from 'eslint-plugin-vue';
import vueParser from 'vue-eslint-parser';
import eslintConfigPrettier from 'eslint-config-prettier';

const LICENSE_LINES = [
  'Copyright (C) 2026 github.com/MaloneTalk',
  '',
  'This program is free software: you can redistribute it and/or modify',
  'it under the terms of the GNU Affero General Public License as',
  'published by the Free Software Foundation, either version 3 of the',
  'License, or any later version.',
  '',
  'This program is distributed in the hope that it will be useful',
  'but WITHOUT ANY WARRANTY; without even the implied warranty of',
  'MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the',
  'GNU Affero General Public License for more details.',
  '',
  'You should have received a copy of the GNU Affero General Public License',
  'along with this program.  If not, see <https://www.gnu.org/licenses/>.',
];

const LICENSE_HEADER_JS = ['/*', ...LICENSE_LINES.map(l => ` * ${l}`), ' */'].join('\n');
const LICENSE_HEADER_VUE = ['<!--', ...LICENSE_LINES.map(l => ` * ${l}`), ' -->'].join('\n');

// 检查文本是否包含完整的 AGPLv3 许可证声明
function hasValidLicense(text) {
  const contentStr = text.replace(/\s+/g, ' ').trim();

  const requiredPatterns = [
    /Copyright\s*\(C\)\s*2026\s*github\.com\/MaloneTalk/i,
    /GNU\s+Affero\s+General\s+Public\s+License/i,
    /version\s+3/i,
    /Free\s+Software\s+Foundation/i,
  ];

  return requiredPatterns.every(pattern => pattern.test(contentStr));
}

// 从 Vue/HTML 文件源码中提取第一个 HTML 注释的内容和范围
function extractVueHtmlComment(sourceText) {
  const match = sourceText.match(/^<!--([\s\S]*?)-->/);
  if (match) {
    const fullMatch = match[0];
    const content = match[1];
    return {
      found: true,
      content,
      start: 0,
      end: fullMatch.length,
    };
  }
  return { found: false };
}

const licenseHeaderPlugin = {
  rules: {
    'license-header': {
      meta: {
        type: 'layout',
        fixable: 'code',
        schema: [],
      },
      create(context) {
        return {
          Program() {
            const sourceCode = context.sourceCode;
            const sourceText = sourceCode.text;
            const isVue = context.filename.endsWith('.vue') || context.filename.endsWith('.html');
            const header = isVue ? LICENSE_HEADER_VUE : LICENSE_HEADER_JS;

            if (isVue) {
              // Vue/HTML 文件：检查开头的 HTML 注释
              const htmlComment = extractVueHtmlComment(sourceText);
              if (htmlComment.found && hasValidLicense(htmlComment.content)) {
                return;
              }

              context.report({
                loc: { line: 1, column: 0 },
                message: '文件缺少许可证声明',
                fix(fixer) {
                  // 在文件开头插入
                  return fixer.insertTextBeforeRange([0, 0], header + '\n\n');
                },
              });
            } else {
              // JS/TS 文件：检查第一个注释
              const comments = sourceCode.getAllComments();
              const first = comments[0];

              if (first && first.range[0] === 0 && hasValidLicense(first.value)) {
                return;
              }

              context.report({
                loc: { line: 1, column: 0 },
                message: '文件缺少许可证声明',
                fix(fixer) {
                  // 在文件开头插入
                  return fixer.insertTextBeforeRange([0, 0], header + '\n\n');
                },
              });
            }
          },
        };
      },
    },
  },
};

export default tseslint.config(
  {
    ignores: ['node_modules/**', 'dist/**', 'vite.config.d.ts', '*.tsbuildinfo'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...vuePlugin.configs['flat/essential'],
  eslintConfigPrettier,
  {
    plugins: { 'license-header-plugin': licenseHeaderPlugin },
    rules: {
      'license-header-plugin/license-header': 'error',
    },
  },
  {
    files: ['**/*.vue', '**/*.html'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tseslint.parser,
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
    },
    rules: {
      'vue/multi-word-component-names': 'off',
      'vue/no-unused-components': 'error',
      'vue/require-default-prop': 'error',
      'vue/require-prop-types': 'error',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    },
  },
  {
    files: ['**/*.ts', '**/*.tsx'],
    languageOptions: {
      parser: tseslint.parser,
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      'prefer-const': 'error',
      'no-var': 'error',
      'object-shorthand': 'error',
      'quote-props': ['error', 'as-needed'],
      'no-console': process.env.NODE_ENV === 'production' ? 'warn' : 'off',
      'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'off',
    },
  },
  {
    files: ['**/*.js', '**/*.jsx'],
    languageOptions: {
      globals: {
        process: 'readonly',
      },
    },
    rules: {
      'no-unused-vars': 'warn',
      'prefer-const': 'error',
      'no-var': 'error',
      'object-shorthand': 'error',
      'quote-props': ['error', 'as-needed'],
      'no-console': process.env.NODE_ENV === 'production' ? 'warn' : 'off',
      'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'off',
    },
  },
);
