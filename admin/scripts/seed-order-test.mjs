import { readFile } from 'node:fs/promises'
import pg from 'pg'
import { randomUUID } from 'node:crypto'

/** 仅供浏览器测试构造历史事实：直接连接临时测试schema，绝不提供HTTP模拟付款入口。 */
const { schema } = JSON.parse(await readFile('.local/e2e-schema.json', 'utf8'))
if (!/^pc1_[0-9a-f]{32}$/.test(schema)) throw new Error('拒绝访问非测试schema')
const url = new URL(
  (process.env.TEST_DB_URL || 'jdbc:postgresql://localhost:5432/han_menu_test').replace(
    /^jdbc:/,
    '',
  ),
)
if (!url.pathname.endsWith('_test')) throw new Error('仅允许独立测试库')
const db = new pg.Client({
  host: url.hostname,
  port: Number(url.port || 5432),
  database: decodeURIComponent(url.pathname.slice(1)),
  user: process.env.TEST_DB_USERNAME || 'han_menu',
  password: process.env.TEST_DB_PASSWORD,
})
const options = JSON.parse(process.argv[2] || '{}')
const allowed = [
  'UNPAID',
  'PAID',
  'ACCEPTED',
  'DELIVERING',
  'COMPLETED',
  'CANCELLING',
  'REFUNDING',
  'CANCELLED',
]
if (!allowed.includes(options.status || 'PAID')) throw new Error('未知测试状态')
await db.connect()
try {
  await db.query(`SET search_path TO ${schema}`)
  await db.query('BEGIN')
  const items = []
  for (let i = 0; i < (options.count || 1); i++) {
    const id = randomUUID(),
      customerId = options.customerId || randomUUID(),
      paymentId = randomUUID(),
      refundId = randomUUID(),
      status = options.status || 'PAID'
    const created = options.createdAt
      ? new Date(options.createdAt)
      : new Date(Date.now() - 120000 - i * 1000)
    const at = (offset) => new Date(created.getTime() + offset * 1000)
    const paid =
      !['UNPAID', 'CANCELLING'].includes(status) && (status !== 'CANCELLED' || options.refundStatus)
    const refund = options.refundStatus || (status === 'REFUNDING' ? 'PENDING' : 'NONE')
    const hasPayment = paid || status === 'CANCELLING'
    if (hasPayment)
      await db.query(
        `INSERT INTO payment_intent(id,business_ref,customer_id,amount,idempotency_key,fingerprint,created_at,expires_at,status,trade_no,paid_at,close_requested,version) VALUES($1,$2,$3,48,$4,'test-fingerprint',$5,$6,$7,$8,$9,false,0)`,
        [
          paymentId,
          id,
          customerId,
          id,
          created,
          at(900),
          paid ? 'SUCCEEDED' : 'PENDING',
          paid ? `test-${id}` : null,
          paid ? at(10) : null,
        ],
      )
    if (refund !== 'NONE')
      await db.query(
        `INSERT INTO payment_refund(id,payment_id,business_ref,customer_id,trade_no,amount,created_at,status,confirmed_at,version) VALUES($1,$2,$3,$4,$5,48,$6,$7,$8,0)`,
        [
          refundId,
          paymentId,
          id,
          customerId,
          `test-${id}`,
          at(50),
          refund,
          refund === 'SUCCEEDED' ? at(60) : null,
        ],
      )
    await db.query(
      `INSERT INTO ordering_order(id,customer_id,idempotency_key,request_fingerprint,source_id,source_version,recipient_name,phone,province,city,district,detail,total,status,version,created_at,cancelled_at,payment_id,paid_at,accepted_at,delivered_at,completed_at,cancel_reason,refund_status,refund_id,reminder_count,last_reminded_at)
 VALUES($1,$2,$3,'test-fingerprint',$4,0,'林女士',$5,'浙江省','杭州市','西湖区','文三路88号',48,$6,0,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18)`,
      [
        id,
        customerId,
        id,
        randomUUID(),
        options.phone || '+13800138009',
        status,
        created,
        status === 'CANCELLED' ? at(60) : null,
        hasPayment ? paymentId : null,
        paid ? at(10) : null,
        ['ACCEPTED', 'DELIVERING', 'COMPLETED'].includes(status) ? at(20) : null,
        ['DELIVERING', 'COMPLETED'].includes(status) ? at(30) : null,
        status === 'COMPLETED' ? at(40) : null,
        ['REFUNDING', 'CANCELLED', 'CANCELLING'].includes(status) ? 'CUSTOMER' : null,
        refund,
        refund === 'SUCCEEDED' ? refundId : null,
        options.reminders || 0,
        options.reminders ? at(45) : null,
      ],
    )
    for (const [position, line] of [
      { name: '清汤牛肉面', kind: 'DISH', price: 32, selections: { 辣度: '微辣' }, components: [] },
      {
        name: '鲜虾小馄饨套餐',
        kind: 'SET_MEAL',
        price: 16,
        selections: {},
        components: [
          {
            productId: randomUUID(),
            name: '鲜虾小馄饨',
            quantity: 1,
            selections: { 辣度: '不辣' },
          },
        ],
      },
    ].entries()) {
      await db.query(
        'INSERT INTO ordering_line(id,order_id,position,product_id,kind,name,unit_price,quantity,selections,components) VALUES($1,$2,$3,$4,$5,$6,$7,1,$8::jsonb,$9::jsonb)',
        [
          randomUUID(),
          id,
          position,
          randomUUID(),
          line.kind,
          line.name,
          line.price,
          JSON.stringify(line.selections),
          JSON.stringify(line.components),
        ],
      )
    }
    items.push({
      id,
      customerId,
      paymentId: hasPayment ? paymentId : null,
      createdAt: created.toISOString(),
      status,
      version: 0,
    })
  }
  await db.query('COMMIT')
  process.stdout.write(JSON.stringify(items))
} catch (error) {
  await db.query('ROLLBACK')
  throw error
} finally {
  await db.end()
}
