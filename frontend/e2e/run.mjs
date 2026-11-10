import { chromium } from 'playwright-core'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const BASE = process.env.E2E_BASE ?? 'http://localhost:5180'
const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025'
const SHOTS = 'D:/web-ai-code/KDShop/.shots/e2e'
fs.mkdirSync(SHOTS, { recursive: true })

const results = []
let page

function log(msg) { console.log(msg) }

async function api(path, { method = 'GET', body } = {}) {
  return page.evaluate(async ({ path, method, body }) => {
    let raw = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
    if (!raw) {
      await fetch('/api/v1/auth/csrf', { credentials: 'include' })
      raw = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
    }
    const token = raw ? decodeURIComponent(raw.split('=').slice(1).join('=')) : null
    const res = await fetch('/api/v1' + path, {
      method,
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...(token ? { 'X-XSRF-TOKEN': token } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    })
    const json = await res.json().catch(() => null)
    return { status: res.status, json }
  }, { path, method, body })
}

async function login(email, password) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[type=email]', email)
  await page.fill('input[type=password]', password)
  await page.click('button.submit')
  await page.waitForTimeout(1500)
  assert.ok(!page.url().includes('/login'), 'login should leave /login for ' + email)
}

async function logout() {
  await page.evaluate(() => fetch('/api/v1/auth/logout', { method: 'POST' }).catch(() => {}))
  await page.context().clearCookies()
}

async function shot(name) {
  await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: false })
}

async function mailTokenFor(email) {
  for (let i = 0; i < 15; i++) {
    const res = await fetch(`${MAILPIT}/api/v1/search?query=to:${encodeURIComponent(email)}`)
    if (res.ok) {
      const data = await res.json()
      const messages = data.messages ?? []
      if (messages.length) {
        const detail = await (await fetch(`${MAILPIT}/api/v1/message/${messages[0].ID}`)).json()
        const text = JSON.stringify(detail)
        const match = text.match(/token=([a-zA-Z0-9-]+)/)
        if (match) return match[1]
      }
    }
    await new Promise((r) => setTimeout(r, 800))
  }
  return null
}

const scenarios = [
  {
    name: '注册与邮箱验证',
    async run() {
      const email = `e2e+${Date.now()}@kdshop.dev`
      await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
      const reg = await api('/auth/register', { method: 'POST', body: { email, password: 'Demo@123456', nickname: 'E2E 顾客' } })
      assert.equal(reg.json.code, 0, 'register failed: ' + JSON.stringify(reg.json))
      const token = await mailTokenFor(email)
      assert.ok(token, 'verification email not found in Mailpit')
      const verify = await api('/auth/verify-email', { method: 'POST', body: { token } })
      assert.equal(verify.json.code, 0, 'verify failed: ' + JSON.stringify(verify.json))
      await login(email, 'Demo@123456')
      const me = await api('/auth/me')
      assert.equal(me.json.data?.emailVerified, true, JSON.stringify(me.json))
      await logout()
      return { email }
    },
  },
  {
    name: '领券中心领取优惠券',
    async run() {
      await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
      const adminLogin = await api('/auth/login', { method: 'POST', body: { email: 'admin@kdshop.dev', password: 'Demo@123456' } })
      assert.equal(adminLogin.json.code, 0, 'admin login failed')
      const name = 'E2E 专享券 ' + Date.now().toString().slice(-6)
      const created = await api('/admin/coupons', { method: 'POST', body: {
        name, type: 'FIXED', value: 300, minAmount: 0, total: 50,
        startsAt: '2026-01-01T00:00:00', endsAt: '2027-12-31T23:59:59',
      } })
      assert.equal(created.json.code, 0, 'create coupon failed: ' + JSON.stringify(created.json))
      const couponId = created.json.data.couponId
      await logout()
      await login('customer@kdshop.dev', 'Demo@123456')
      await page.goto(BASE + '/coupons', { waitUntil: 'networkidle' })
      await page.waitForTimeout(1000)
      const row = page.locator('article.ticket').filter({ hasText: name }).first()
      assert.ok(await row.count(), 'new coupon not listed')
      await row.getByRole('button', { name: '领取', exact: true }).click()
      await page.waitForTimeout(1500)
      const mine = await api('/me/coupons')
      assert.ok((mine.json.data ?? []).some((c) => c.couponId === couponId), 'coupon not in my list')
      return { couponId }
    },
  },
  {
    name: '加购、用券下单与模拟支付',
    async run() {
      await login('customer@kdshop.dev', 'Demo@123456')
      await logout()
      await login('customer@kdshop.dev', 'Demo@123456')
      let targetId = null
      const list = await api('/products?size=60')
      for (const item of list.json.data?.items ?? list.json.data ?? []) {
        const d = await api('/products/' + item.id)
        const p = d.json.data
        if (p && p.shop?.id === 1 && !p.requiresFlawAck && (p.options?.length ?? 0) === 0
            && (p.skus ?? []).some((k) => k.stock > 0)) { targetId = p.id; break }
      }
      assert.ok(targetId, 'no simple in-stock product found for merchant 1')
      await page.goto(BASE + '/products/' + targetId, { waitUntil: 'networkidle' })
      await page.waitForTimeout(700)
      await page.getByRole('button', { name: '加入购物车' }).click()
      await page.waitForTimeout(900)
      await page.goto(BASE + '/cart', { waitUntil: 'networkidle' })
      await page.waitForTimeout(800)
      await page.getByRole('checkbox').first().check()
      await page.getByRole('button', { name: '去结算' }).click()
      await page.waitForURL('**/checkout', { timeout: 15000 })
      await page.waitForTimeout(1200)
      await page.locator('button.address').first().click()
      const coupon = page.locator('button.coupon').filter({ hasText: '无门槛' }).first()
      if (await coupon.count()) await coupon.click()
      await page.waitForTimeout(600)
      await page.getByRole('button', { name: '提交订单' }).click()
      await page.waitForTimeout(2000)
      const payBtn = page.getByRole('button', { name: '去支付' })
      if (await payBtn.count()) {
        await payBtn.click()
        await page.waitForURL('**/mock-pay/**', { timeout: 15000 })
        await page.waitForTimeout(800)
        await page.getByRole('button', { name: '模拟支付成功' }).click()
        await page.waitForTimeout(1500)
      }
      await page.goto(BASE + '/orders', { waitUntil: 'networkidle' })
      await page.waitForTimeout(1000)
      const first = page.locator('a[href^="/orders/"]').first()
      const href = await first.getAttribute('href')
      assert.ok(href, 'no order found after checkout')
      const orderNo = href.split('/').pop()
      const detail = await api('/orders/' + orderNo)
      assert.ok(['PAID', 'PENDING_SHIPMENT', 'PARTIALLY_SHIPPED'].includes(detail.json.data.status), 'unexpected order status ' + detail.json.data.status)
      return { orderNo }
    },
  },
  {
    name: '商家发货',
    async run(ctx) {
      await logout()
      await login('merchant1@kdshop.dev', 'Demo@123456')
      await page.goto(BASE + '/merchant/orders', { waitUntil: 'networkidle' })
      await page.waitForTimeout(1200)
      const card = page.locator('article, .order, .sub').filter({ hasText: ctx.orderNo }).first()
      const scope = (await card.count()) ? card : page
      const ship = scope.getByRole('button', { name: '发货', exact: true }).first()
      assert.ok(await ship.count(), 'no pending shipment for ' + ctx.orderNo)
      await ship.click()
      await page.waitForTimeout(800)
      const dialog = page.locator('.el-dialog:visible').last()
      await dialog.waitFor({ state: 'visible', timeout: 8000 })
      await dialog.locator('input').last().fill('SF' + Date.now().toString().slice(-10))
      await dialog.getByRole('button', { name: '确认发货' }).click()
      await page.waitForTimeout(1500)
      const list2 = await api('/merchant/sub-orders?status=SHIPPED')
      const sub = (list2.json.data ?? []).find((x) => x.subOrderNo === ctx.orderNo + '-1')
      assert.ok(sub, 'sub order not shipped')
      assert.ok(sub.trackingNo, 'tracking number missing')
      return { trackingNo: sub.trackingNo }
    },
  },
  {
    name: '确认收货与评价',
    async run(ctx) {
      await logout()
      await login('customer@kdshop.dev', 'Demo@123456')
      await page.goto(BASE + '/orders/' + ctx.orderNo, { waitUntil: 'networkidle' })
      await page.waitForTimeout(1200)
      const confirm = page.getByRole('button', { name: '确认收货' }).first()
      if (await confirm.count()) {
        await confirm.click()
        await page.waitForTimeout(500)
        await page.locator('.el-message-box__btns button').last().click()
        await page.waitForTimeout(2000)
      }
      const detail = await api('/orders/' + ctx.orderNo)
      assert.equal(detail.json.data.subOrders[0].status, 'COMPLETED')
      await page.reload({ waitUntil: 'networkidle' })
      await page.waitForTimeout(800)
      const reviewBtn = page.getByRole('button', { name: '评价' }).first()
      assert.ok(await reviewBtn.count(), 'review button missing')
      await reviewBtn.click()
      await page.waitForTimeout(700)
      await page.locator('.el-dialog textarea').fill('E2E：质感很好，和描述一致。')
      await page.getByRole('button', { name: '提交评价' }).click()
      await page.waitForTimeout(1500)
      assert.ok(await page.getByText('已评价').first().count(), 'review state not updated')
    },
  },
  {
    name: '退款申请与平台裁决',
    async run(ctx) {
      await logout()
      await login('customer@kdshop.dev', 'Demo@123456')
      await page.goto(BASE + '/orders/' + ctx.orderNo, { waitUntil: 'networkidle' })
      await page.waitForTimeout(1000)
      await page.getByRole('button', { name: '申请退款' }).first().click()
      await page.waitForTimeout(600)
      await page.locator('.el-dialog textarea').fill('E2E：不想要了')
      await page.getByRole('button', { name: '提交申请' }).click()
      await page.waitForTimeout(1800)
      const mine = await api('/refunds')
      const refund = (mine.json.data ?? []).find((r) => r.subOrderNo === ctx.orderNo + '-1' && r.status === 'PENDING')
      assert.ok(refund || (mine.json.data ?? []).some((r) => r.status === 'PENDING'), 'refund not created')
      await logout()
      await login('admin@kdshop.dev', 'Demo@123456')
      await page.goto(BASE + '/admin/refunds', { waitUntil: 'networkidle' })
      await page.waitForTimeout(1200)
      await page.getByRole('button', { name: '判给买家' }).first().click()
      await page.waitForTimeout(600)
      await page.getByRole('button', { name: '确定' }).click()
      await page.waitForTimeout(1800)
      const all = await api('/admin/refunds?status=REFUNDED')
      assert.ok((all.json.data ?? []).length > 0, 'no refunded record')
    },
  },
]

const ctx = {}

async function main() {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  page = await context.newPage()
  page.setDefaultTimeout(15000)

  for (const s of scenarios) {
    const started = Date.now()
    try {
      const data = await s.run(ctx)
      Object.assign(ctx, data ?? {})
      results.push({ name: s.name, ok: true, ms: Date.now() - started })
      log('PASS ' + s.name)
    } catch (e) {
      await shot('fail-' + s.name.replace(/[^a-z0-9\u4e00-\u9fa5]/gi, '_'))
      results.push({ name: s.name, ok: false, ms: Date.now() - started, error: String(e?.message ?? e) })
      log('FAIL ' + s.name + ' :: ' + (e?.message ?? e))
    }
  }

  await browser.close()
  const passed = results.filter((r) => r.ok).length
  console.log('\nE2E 结果：' + passed + '/' + results.length + ' 通过')
  for (const r of results) console.log(`  ${r.ok ? '✓' : '✗'} ${r.name} (${r.ms}ms)` + (r.ok ? '' : '\n    ' + r.error))
  fs.writeFileSync('D:/web-ai-code/KDShop/.shots/e2e-report.json', JSON.stringify({ at: new Date().toISOString(), results }, null, 2))
  process.exit(passed === results.length ? 0 : 1)
}

main().catch((e) => { console.error(e); process.exit(1) })
