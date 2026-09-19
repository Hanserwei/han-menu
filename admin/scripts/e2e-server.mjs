import pg from 'pg'
import { randomUUID } from 'node:crypto'
import { spawn } from 'node:child_process'
import { once } from 'node:events'

/** 浏览器验收独立测试库及临时 schema，退出后先停应用再清理；禁止指向开发库。 */
const url = (process.env.TEST_DB_URL || 'jdbc:postgresql://localhost:5432/han_menu_test').replace(
  /^jdbc:/,
  '',
)
if (!new URL(url).pathname.endsWith('_test')) throw new Error('浏览器测试只允许使用 _test 数据库')
if (!process.env.TEST_DB_PASSWORD)
  throw new Error('需要 TEST_DB_PASSWORD，请通过 scripts/with-env.sh 启动')
const schema = `pc1_${randomUUID().replaceAll('-', '')}`
const endpoint = new URL(url)
const database = new pg.Client({
  host: endpoint.hostname,
  port: Number(endpoint.port || 5432),
  database: decodeURIComponent(endpoint.pathname.slice(1)),
  user: process.env.TEST_DB_USERNAME || 'han_menu',
  password: process.env.TEST_DB_PASSWORD,
})
await database.connect()
await database.query(`CREATE SCHEMA ${schema}`)
const child = spawn(
  'java',
  [
    '-jar',
    '../target/han-menu-0.0.1-SNAPSHOT.jar',
    `--han-menu.identity.redis-key-prefix=han-menu:test:${schema}:login:`,
    `--han-menu.customer.redis-key-prefix=han-menu:test:${schema}:customer:`,
  ],
  {
    stdio: ['ignore', 'ignore', 'inherit'],
    env: {
      ...process.env,
      SERVER_PORT: '18081',
      SERVER_ADDRESS: '127.0.0.1',
      DB_URL: `jdbc:${url}`,
      DB_USERNAME: process.env.TEST_DB_USERNAME || 'han_menu',
      DB_PASSWORD: process.env.TEST_DB_PASSWORD,
      SPRING_DATASOURCE_HIKARI_SCHEMA: schema,
      SPRING_FLYWAY_DEFAULT_SCHEMA: schema,
      IDENTITY_BOOTSTRAP_ENABLED: 'true',
      IDENTITY_BOOTSTRAP_USERNAME: 'pc1admin',
      IDENTITY_BOOTSTRAP_PASSWORD: 'Pc1-local-admin-2026!',
      PAYMENT_SCHEDULING_ENABLED: 'false',
      NOTIFICATION_SCHEDULING_ENABLED: 'false',
      EVENT_RECOVERY_ENABLED: 'false',
      REPORTING_BOOTSTRAP_ENABLED: 'true',
      ALIPAY_PRIVATE_KEY: '',
      ALIPAY_PUBLIC_KEY: '',
      ALIPAY_APP_ID: '',
      ALIPAY_NOTIFY_URL: '',
      ALIPAY_SELLER_ID: '',
      RUSTFS_BUCKET: process.env.RUSTFS_TEST_BUCKET || 'han-menu-test',
    },
  },
)
let closing = false
async function close() {
  if (closing) return
  closing = true
  if (child.exitCode === null && child.signalCode === null) {
    child.kill('SIGTERM')
    await once(child, 'exit')
  }
  await database.query(`DROP SCHEMA ${schema} CASCADE`)
  await database.end()
}
process.on('SIGTERM', () => {
  void close().then(() => process.exit(0))
})
process.on('SIGINT', () => {
  void close().then(() => process.exit(0))
})
child.on('error', () => {
  void close().then(() => process.exit(1))
})
child.on('exit', () => {
  if (!closing) void close().then(() => process.exit(1))
})
