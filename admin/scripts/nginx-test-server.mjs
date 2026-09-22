import { mkdtemp, readFile, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { randomUUID } from 'node:crypto'
import { spawn, spawnSync, execFileSync } from 'node:child_process'
import { renderNginx } from '../deploy/render-nginx.mjs'

/** 只在回环端口启动临时HTTPS代理，用生产dist和正式模板验证发布边界；不复用开发服务器。 */
const runtime =
  process.env.ADMIN_CONTAINER_RUNTIME ||
  (spawnSync('podman', ['--version']).status === 0 ? 'podman' : 'docker')
if (!['podman', 'docker'].includes(runtime))
  throw new Error('ADMIN_CONTAINER_RUNTIME仅允许podman或docker')
if (spawnSync(runtime, ['info'], { stdio: 'ignore' }).status !== 0)
  throw new Error('需要可运行的Podman或Docker以验证真实Nginx')
await readFile('dist/index.html')
const { nginxImage } = JSON.parse(await readFile('deploy/runtime.json', 'utf8'))
const template = await readFile('deploy/nginx.conf.template', 'utf8')
const directory = await mkdtemp(join(tmpdir(), 'han-menu-nginx-test-'))
const names = [],
  children = []
let closing = false
async function close(code = 0) {
  if (closing) return
  closing = true
  for (const name of names) spawnSync(runtime, ['stop', '--time', '5', name], { stdio: 'ignore' })
  await rm(directory, { recursive: true, force: true })
  process.exit(code)
}
process.on('SIGTERM', () => void close())
process.on('SIGINT', () => void close())
try {
  // 证书仅用于临时回环验收，测试浏览器单独信任；正式部署必须使用受信任证书。
  execFileSync(
    'openssl',
    [
      'req',
      '-x509',
      '-newkey',
      'rsa:2048',
      '-nodes',
      '-days',
      '1',
      '-subj',
      '/CN=localhost',
      '-addext',
      'subjectAltName=IP:127.0.0.1,DNS:localhost',
      '-keyout',
      join(directory, 'test.key'),
      '-out',
      join(directory, 'test.crt'),
    ],
    { stdio: 'ignore' },
  )
  for (const [port, backend] of [
    [15174, '127.0.0.1:18081'],
    [15175, '127.0.0.1:18089'],
  ]) {
    const name = `han-menu-pc6-${randomUUID()}`
    names.push(name)
    const config = renderNginx(template, {
      listen: `127.0.0.1:${port}`,
      backend,
      root: '/srv/han-menu',
      serverName: '127.0.0.1',
      certificate: '/etc/han-menu/test.crt',
      key: '/etc/han-menu/test.key',
    })
    await writeFile(join(directory, `${port}.conf`), config)
    const args = [
      'run',
      '--rm',
      '--network',
      'host',
      '--read-only',
      '--tmpfs',
      '/tmp',
      '--tmpfs',
      '/var/cache/nginx',
      '-v',
      `${resolve('dist')}:/srv/han-menu:ro,z`,
      '-v',
      `${directory}:/etc/han-menu:ro,z`,
      '--entrypoint',
      'nginx',
      nginxImage,
      '-c',
      `/etc/han-menu/${port}.conf`,
    ]
    execFileSync(runtime, [...args, '-t'], { stdio: ['ignore', 'ignore', 'inherit'] })
    const child = spawn(
      runtime,
      [...args.slice(0, 1), '--name', name, ...args.slice(1), '-g', 'daemon off;'],
      { stdio: ['ignore', 'ignore', 'inherit'] },
    )
    children.push(child)
    child.on('error', () => void close(1))
    child.on('exit', () => {
      if (!closing) void close(1)
    })
  }
} catch (failure) {
  console.error(failure instanceof Error ? failure.message : 'Nginx验收启动失败')
  await close(1)
}
