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
import eslintConfigPrettier from 'eslint-config-prettier';
import globals from 'globals';

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

const LICENSE_HEADER = ['/*', ...LICENSE_LINES.map(line => ` * ${line}`), ' */'].join('\n');

function hasValidLicense(text) {
  const content = text.replace(/\s+/g, ' ').trim();
  const requiredPatterns = [
    /Copyright\s*\(C\)\s*2026\s*github\.com\/MaloneTalk/i,
    /GNU\s+Affero\s+General\s+Public\s+License/i,
    /version\s+3/i,
    /Free\s+Software\s+Foundation/i,
  ];
  return requiredPatterns.every(pattern => pattern.test(content));
}

function licenseHeaderOffset(sourceText) {
  return sourceText.startsWith('#!') ? sourceText.indexOf('\n') + 1 : 0;
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
            const offset = licenseHeaderOffset(sourceText);
            const firstComment = sourceCode
              .getAllComments()
              .find(comment => comment.range[0] === offset);

            if (firstComment && hasValidLicense(firstComment.value)) {
              return;
            }

            context.report({
              loc: { line: 1, column: 0 },
              message: '文件缺少许可证声明',
              fix(fixer) {
                return fixer.insertTextBeforeRange([offset, offset], LICENSE_HEADER + '\n\n');
              },
            });
          },
        };
      },
    },
  },
};

export default tseslint.config(
  {
    ignores: ['node_modules/**', 'dist/**', 'coverage/**', '*.tsbuildinfo'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  eslintConfigPrettier,
  {
    plugins: { 'license-header-plugin': licenseHeaderPlugin },
    rules: {
      'license-header-plugin/license-header': 'error',
    },
  },
  {
    files: ['**/*.js', '**/*.ts'],
    languageOptions: {
      parser: tseslint.parser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
      globals: globals.node,
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      'prefer-const': 'error',
      'no-var': 'error',
      'object-shorthand': 'error',
      'quote-props': ['error', 'as-needed'],
      'no-console': 'off',
      'no-debugger': 'error',
    },
  },
);
