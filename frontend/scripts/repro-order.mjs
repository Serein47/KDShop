import { chromium } from 'playwright-core';
const base = 'http://127.0.0.1:18081';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
page.on('response', async (res) => {
  if (res.url().includes('/api/v1/orders') && res.request().method() === 'POST') {
    console.log('ORDER_RESP', res.status(), (await res.text()).slice(0, 400));
  }
});
await page.goto(base + '/login', { waitUntil: 'load' });
await page.fill('input[type=email]', 'customer@kdshop.dev');
await page.fill('input[type=password]', 'Demo@123456');
await page.click('button.submit');
await page.waitForTimeout(2000);
const cartAdd = await page.evaluate(async () => {
  const token = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.split('=')[1];
  const r = await fetch('/api/v1/cart/items', {
    method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(token) },
    body: JSON.stringify({ skuId: 1, quantity: 1 }),
  });
  return { status: r.status, body: (await r.text()).slice(0, 200) };
});
console.log('CART_ADD', JSON.stringify(cartAdd));
await page.goto(base + '/cart', { waitUntil: 'load' });
await page.waitForTimeout(1500);
await page.getByRole('checkbox').first().check();
await page.getByRole('button', { name: '去结算' }).click();
await page.waitForTimeout(2500);
await page.locator('button.address').first().click();
await page.waitForTimeout(500);
await page.getByRole('button', { name: '提交订单' }).click();
await page.waitForTimeout(3000);
console.log('URL', page.url());
const msg = await page.locator('.el-message, .error, .hint, .done__hint').allInnerTexts().catch(() => []);
console.log('MSG', JSON.stringify(msg).slice(0, 400));
await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/repro-order.png', fullPage: false });
await browser.close();
