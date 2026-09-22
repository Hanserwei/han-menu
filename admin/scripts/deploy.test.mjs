import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { renderNginx } from '../deploy/render-nginx.mjs'
const template = await readFile(new URL('../deploy/nginx.conf.template', import.meta.url), 'utf8')
const options = {
  listen: '127.0.0.1:15174',
  backend: '127.0.0.1:18081',
  root: '/srv/han-menu/current',
  serverName: 'localhost',
}
test('模板保留Nginx变量、禁止写重试，并覆盖重建超时', () => {
  const output = renderNginx(template, options)
  assert.ok(output.includes('proxy_set_header Host $http_host;'))
  assert.ok(output.includes('try_files $uri =404;'))
  assert.ok(output.includes('proxy_next_upstream off;'))
  assert.ok(output.includes('proxy_read_timeout 210s;'))
  assert.ok(!output.includes('{{'))
})
test('公开绑定必须HTTPS，证书成对，拒绝配置注入与越界端口', () => {
  for (const invalid of [
    { listen: '0.0.0.0:443' },
    { listen: '127.0.0.1:99999' },
    { certificate: '/cert.pem' },
    { root: '/tmp/a"; include /etc/passwd;' },
    { serverName: 'localhost; return 200;' },
    { backend: 'http://localhost:8080' },
  ])
    assert.throws(() => renderNginx(template, { ...options, ...invalid }))
  assert.ok(
    renderNginx(template, {
      ...options,
      listen: '0.0.0.0:443',
      certificate: '/etc/tls/cert.pem',
      key: '/etc/tls/key.pem',
    }).includes('listen 0.0.0.0:443 ssl;'),
  )
})
