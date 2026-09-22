import { describe, it, expect } from 'vitest'
import { validateWorkbook } from './reports'
describe('后端XLSX下载边界', () => {
  it('保留后端文件，不重新生成金额与工作簿', async () => {
    const response = new Response('PK workbook', {
      headers: {
        'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      },
    })
    const blob = await response.blob()
    expect(await validateWorkbook(blob, response)).toBe(blob)
  })
  it('200 JSON Problem也不能保存为xlsx，并保留追踪信息', async () => {
    const response = new Response(
      JSON.stringify({ detail: '投影未就绪', code: 'REPORTING_NOT_READY', traceId: 'trace-1' }),
      { headers: { 'Content-Type': 'application/problem+json' } },
    )
    await expect(validateWorkbook(await response.blob(), response)).rejects.toMatchObject({
      message: '投影未就绪',
      code: 'REPORTING_NOT_READY',
      traceId: 'trace-1',
    })
  })
  it('拒绝HTML错误页和空文件', async () => {
    const html = new Response('<html>error</html>', { headers: { 'Content-Type': 'text/html' } })
    await expect(validateWorkbook(await html.blob(), html)).rejects.toMatchObject({
      code: 'INVALID_EXPORT',
    })
    const empty = new Response('', {
      headers: {
        'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      },
    })
    await expect(validateWorkbook(await empty.blob(), empty)).rejects.toMatchObject({
      code: 'INVALID_EXPORT',
    })
  })
})
