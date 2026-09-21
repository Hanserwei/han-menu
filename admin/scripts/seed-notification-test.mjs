import { readFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import pg from 'pg'

/** 仅向浏览器运行器的随机_test schema登记测试通知；真实推送仍由原服务端工作器执行。 */
const { schema } = JSON.parse(await readFile('.local/e2e-schema.json', 'utf8'))
if (!/^pc1_[0-9a-f]{32}$/.test(schema)) throw new Error('拒绝非隔离测试schema')
const url = new URL(
  (process.env.TEST_DB_URL || 'jdbc:postgresql://localhost:5432/han_menu_test').replace(
    /^jdbc:/,
    '',
  ),
)
if (!url.pathname.endsWith('_test')) throw new Error('仅允许独立测试库')
const options = JSON.parse(process.argv[2] || '{}')
const count = options.count ?? 1
if (!Number.isInteger(count) || count < 1 || count > 200) throw new Error('测试批次越界')
const kind = options.type || 'NEW_ORDER'
if (!['NEW_ORDER', 'ORDER_REMINDER'].includes(kind)) throw new Error('未知通知类型')
const database = new pg.Client({
  host: url.hostname,
  port: Number(url.port || 5432),
  database: decodeURIComponent(url.pathname.slice(1)),
  user: process.env.TEST_DB_USERNAME || 'han_menu',
  password: process.env.TEST_DB_PASSWORD,
})
await database.connect()
try {
  await database.query(`SET search_path TO ${schema}`)
  await database.query('BEGIN')
  const head = await database.query('SELECT sequence FROM notification_feed WHERE id=1 FOR UPDATE')
  const start = Number(head.rows[0].sequence),
    items = []
  for (let i = 1; i <= count; i++) {
    const id = randomUUID(),
      orderId = options.orderId || randomUUID(),
      sequence = start + i
    await database.query(
      `INSERT INTO notification_notice(id,sequence,order_id,kind,occurred_at,created_at,status,attempts,failures,next_attempt_at,version) VALUES($1,$2,$3,$4,now(),now(),'PENDING',0,0,now(),0)`,
      [id, sequence, orderId, kind],
    )
    items.push({ id, sequence, orderId, type: kind })
  }
  await database.query('UPDATE notification_feed SET sequence=$1 WHERE id=1', [start + count])
  await database.query('COMMIT')
  process.stdout.write(JSON.stringify(items))
} catch (error) {
  await database.query('ROLLBACK')
  throw error
} finally {
  await database.end()
}
