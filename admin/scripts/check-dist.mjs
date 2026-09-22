import { readdir, readFile, lstat } from 'node:fs/promises'
import { resolve, relative } from 'node:path'
/** 只交付静态构建；检查入口引用和源码／测试凭证泄漏，失败直接阻止交付。 */
const root = resolve('dist'),
  files = []
async function visit(dir) {
  for (const name of await readdir(dir)) {
    const path = resolve(dir, name),
      info = await lstat(path)
    if (info.isSymbolicLink()) throw new Error('交付产物不允许符号链接')
    if (info.isDirectory()) await visit(path)
    else files.push(path)
  }
}
await visit(root)
const index = await readFile(resolve(root, 'index.html'), 'utf8')
if (!/<html lang="zh-CN"/.test(index) || !index.includes('/assets/'))
  throw new Error('缺少有效的生产入口')
for (const match of index.matchAll(/(?:src|href)="(\/assets\/[^"?#]+)"/g))
  await lstat(resolve(root, '.' + match[1]))
for (const file of files) {
  const name = relative(root, file)
  if (/\.map$|(^|\/)\.|\.(?:ts|vue|pem|key)$/.test(name)) throw new Error('构建包含禁止交付的文件')
  if (/\.(?:js|html|css)$/.test(name)) {
    const text = await readFile(file, 'utf8')
    if (
      /\/src\/main\.ts|ComponentLab|Pc1-local-admin-2026|ALIPAY_PRIVATE_KEY|TEST_DB_PASSWORD/.test(
        text,
      )
    )
      throw new Error('构建包含开发入口或服务端配置标识')
  }
}
console.log(`生产静态产物检查通过：${files.length}个文件`)
