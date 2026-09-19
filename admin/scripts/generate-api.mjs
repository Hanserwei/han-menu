import { readFile, writeFile } from 'node:fs/promises'
import openapiTS, { astToString } from 'openapi-typescript'

/** 从已提交的服务端契约生成类型；检查模式拒绝契约和类型漂移。 */
const schema = JSON.parse(
  await readFile(new URL('../contracts/openapi.json', import.meta.url), 'utf8'),
)
const output =
  '// 自动生成：依据 contracts/openapi.json，禁止手工修改。\n' +
  astToString(await openapiTS(schema))
const path = new URL('../src/shared/api/schema.d.ts', import.meta.url)
if (process.argv.includes('--check')) {
  if ((await readFile(path, 'utf8')) !== output)
    throw new Error('接口类型已过期，请运行 pnpm api:generate')
} else await writeFile(path, output)
