import pg from 'pg'
import { randomUUID } from 'node:crypto'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { S3Client, ListObjectsV2Command, DeleteObjectsCommand } from '@aws-sdk/client-s3'

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
const imagePrefix = `han-menu-test/${schema}/`
const testBucket = process.env.RUSTFS_TEST_BUCKET || 'han-menu-test'
if (!testBucket.endsWith('-test')) throw new Error('图片验收只允许测试桶')
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
    `--han-menu.catalog.storage.key-prefix=${imagePrefix}`,
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
      RUSTFS_BUCKET: testBucket,
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
  // 仅删除本次随机命名空间的测试图片，绝不清理开发桶或其他测试记录。
  const storage = new S3Client({
    endpoint: process.env.RUSTFS_ENDPOINT || 'http://127.0.0.1:9000',
    region: 'us-east-1',
    forcePathStyle: true,
    credentials: {
      accessKeyId: process.env.RUSTFS_ACCESS_KEY,
      secretAccessKey: process.env.RUSTFS_SECRET_KEY,
    },
  })
  try {
    let continuation
    do {
      const page = await storage.send(
        new ListObjectsV2Command({
          Bucket: testBucket,
          Prefix: imagePrefix,
          ContinuationToken: continuation,
        }),
      )
      const objects = (page.Contents || [])
        .filter((item) => item.Key?.startsWith(imagePrefix))
        .map((item) => ({ Key: item.Key }))
      if (objects.length)
        await storage.send(
          new DeleteObjectsCommand({ Bucket: testBucket, Delete: { Objects: objects } }),
        )
      continuation = page.NextContinuationToken
    } while (continuation)
  } finally {
    storage.destroy()
    await database.query(`DROP SCHEMA ${schema} CASCADE`)
    await database.end()
  }
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
