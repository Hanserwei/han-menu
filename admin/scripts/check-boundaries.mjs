import { readdir, readFile } from 'node:fs/promises'
import { resolve, relative, dirname } from 'node:path'

/** 目录结构是可验证的依赖规则：shared 不依赖业务，模块通过 index 暴露边界。 */
const root = resolve('src')
async function visit(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) await visit(path)
    else if (/\.(vue|ts)$/.test(path) && !/schema\.d\.ts$/.test(path)) {
      const owner = relative(root, path).replaceAll('\\', '/')
      for (const match of (await readFile(path, 'utf8')).matchAll(
        /(?:from\s*|import\s*\()\s*['"]([^'"]+)['"]/g,
      )) {
        const spec = match[1]
        if (!spec.startsWith('@/') && !spec.startsWith('.')) continue
        const target = relative(
          root,
          spec.startsWith('@/') ? resolve(root, spec.slice(2)) : resolve(dirname(path), spec),
        ).replaceAll('\\', '/')
        if (owner.startsWith('shared/') && !target.startsWith('shared/'))
          throw new Error(`${owner} 不能反向依赖 ${target}`)
        if (owner.startsWith('modules/') && target.startsWith('app/'))
          throw new Error(`${owner} 不能依赖应用装配层`)
        const ownModule = owner.split('/')[1]
        const targetModule = target.split('/')[1]
        if (
          target.startsWith('modules/') &&
          (!owner.startsWith('modules/') || ownModule !== targetModule) &&
          !/^modules\/[^/]+(?:\/index(?:\.ts)?)?$/.test(target)
        )
          throw new Error(`${owner} 必须通过模块公开入口访问 ${target}`)
      }
    }
  }
}
await visit(root)
console.log('前端模块依赖检查通过')
