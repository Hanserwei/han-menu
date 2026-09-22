import { mkdtemp, cp, readFile, writeFile, readdir, rm, mkdir, lstat } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { tmpdir } from 'node:os'
import { join, resolve, relative } from 'node:path'
/** 白名单打包已验证产物及部署资料，绝不复制.env、测试账号、node_modules或后端配置。 */
execFileSync(process.execPath, ['scripts/check-dist.mjs'], { stdio: 'inherit' })
const staging = await mkdtemp(join(tmpdir(), 'han-menu-release-'))
try {
  for (const name of ['dist', 'DEPLOYMENT.md'])
    await cp(name, join(staging, name), { recursive: true })
  await mkdir(join(staging, 'deploy'))
  for (const name of ['nginx.conf.template', 'render-nginx.mjs', 'runtime.json'])
    await cp(join('deploy', name), join(staging, 'deploy', name))
  const revision = execFileSync('git', ['rev-parse', '--short=12', 'HEAD'], {
    encoding: 'utf8',
  }).trim()
  const dirty = !!execFileSync('git', ['status', '--porcelain'], { encoding: 'utf8' }).trim()
  const builtAt = new Date().toISOString()
  await writeFile(
    join(staging, 'release.json'),
    JSON.stringify(
      { name: 'han-menu-admin', revision, dirty, builtAt, node: process.version },
      null,
      2,
    ) + '\n',
  )
  const hashes = []
  async function hash(dir) {
    for (const name of (await readdir(dir)).sort()) {
      const path = join(dir, name),
        info = await lstat(path)
      if (info.isSymbolicLink()) throw new Error('打包不允许符号链接')
      if (info.isDirectory()) await hash(path)
      else
        hashes.push(
          `${createHash('sha256')
            .update(await readFile(path))
            .digest('hex')}  ${relative(staging, path)}`,
        )
    }
  }
  await hash(staging)
  await writeFile(join(staging, 'SHA256SUMS'), hashes.join('\n') + '\n')
  const output = resolve('.local/releases')
  await mkdir(output, { recursive: true })
  const file = join(
    output,
    `han-menu-admin-${revision}${dirty ? '-dirty' : ''}-${builtAt.replace(/[:.]/g, '-')}.tar.gz`,
  )
  execFileSync('tar', ['-czf', file, '-C', staging, '.'])
  await writeFile(
    file + '.sha256',
    createHash('sha256')
      .update(await readFile(file))
      .digest('hex') +
      '  ' +
      file.split('/').at(-1) +
      '\n',
  )
  console.log(`交付包：${file}`)
} finally {
  await rm(staging, { recursive: true, force: true })
}
