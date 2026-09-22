import { readFile, writeFile } from 'node:fs/promises'
import { parseArgs } from 'node:util'
import { isAbsolute } from 'node:path'
import { fileURLToPath } from 'node:url'
/** 只替换部署占位符，不展开Nginx自身变量；参数必须符合对应语法，避免配置注入。 */
export function renderNginx(template, options) {
  const { listen, backend, root, serverName, certificate, key } = options
  if (
    !/^(?:127\.0\.0\.1|0\.0\.0\.0):[0-9]{1,5}$/.test(listen || '') ||
    Number(listen.split(':')[1]) > 65535 ||
    Number(listen.split(':')[1]) < 1
  )
    throw new Error('listen必须为本机IPv4绑定地址与有效端口')
  if (
    !/^[a-zA-Z0-9.-]+:[0-9]{1,5}$/.test(backend || '') ||
    Number(backend.split(':')[1]) < 1 ||
    Number(backend.split(':')[1]) > 65535
  )
    throw new Error('backend必须为主机:端口，不包含协议或路径')
  if (!/^[a-zA-Z0-9.-]+$/.test(serverName || ''))
    throw new Error('server-name必须是单个明确域名或IP')
  function quote(path) {
    if (!path || !isAbsolute(path) || /[\r\n\0"$\\]/.test(path))
      throw new Error('路径必须为不含控制字符或Nginx变量的绝对路径')
    return `"${path}"`
  }
  if (!!certificate !== !!key) throw new Error('TLS证书和私钥必须同时提供')
  if (listen.startsWith('0.0.0.0:') && !certificate) throw new Error('对外监听必须配置HTTPS证书')
  const values = {
    LISTEN: listen + (certificate ? ' ssl' : ''),
    BACKEND: backend,
    ROOT: quote(root),
    SERVER_NAME: serverName,
    TLS: certificate
      ? `ssl_certificate ${quote(certificate)};\n    ssl_certificate_key ${quote(key)};\n    ssl_protocols TLSv1.2 TLSv1.3;`
      : '# 本机回环HTTP验收，不用于公开部署。',
  }
  return template.replace(/\{\{(\w+)\}\}/g, (_, name) => {
    if (!(name in values)) throw new Error('未知占位符')
    return values[name]
  })
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const { values } = parseArgs({
    options: {
      listen: { type: 'string' },
      backend: { type: 'string' },
      root: { type: 'string' },
      'server-name': { type: 'string' },
      certificate: { type: 'string' },
      key: { type: 'string' },
      output: { type: 'string' },
    },
  })
  if (!values.output) throw new Error('必须指定--output')
  const template = await readFile(new URL('./nginx.conf.template', import.meta.url), 'utf8')
  await writeFile(
    values.output,
    renderNginx(template, { ...values, serverName: values['server-name'] }),
    { mode: 0o600 },
  )
}
