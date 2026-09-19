import { globalIgnores } from 'eslint/config'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import pluginVue from 'eslint-plugin-vue'
import skipFormatting from 'eslint-config-prettier/flat'

/** 统一 Vue/TypeScript 检查；生成的接口文件由契约一致性检查负责。 */
export default defineConfigWithVueTs(
  globalIgnores([
    'dist/**',
    'node_modules/**',
    'coverage/**',
    'playwright-report/**',
    'test-results/**',
    'src/shared/api/schema.d.ts',
  ]),
  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,
  { files: ['**/*.vue'], rules: { 'vue/multi-word-component-names': 'off' } },
  skipFormatting,
)
