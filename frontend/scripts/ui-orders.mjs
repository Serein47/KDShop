import { chromium } from 'playwright-core';
const base = 'http://localhost:5180';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
const errors = [];
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
page.on('response', (r) => { if (r.url().includes('/api/v1/orders') && r.status() >= 400) errors.push(r.status() + ' ' + r.url()); });

await page.goto(base + '/login', { waitUntil: 'networkidle' });
await page.click('button.submit');
await page.waitForTimeout(1500);
await page.goto(base + '/orders', { waitUntil: 'networkidle' });
await page.waitForTimeout(1500);
console.log('url:', page.url());
console.log('body head:', (await page.locator('body').innerText()).replace(/\n+/g, ' | ').slice(0, 400));
await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/orders-list.png' });

const link = page.locator('a').filter({ hasText: /KD/ }).first();
if (await link.count()) {
  await link.click();
  await page.waitForTimeout(1500);
  console.log('detail url:', page.url());
  console.log('detail:', (await page.locator('body').innerText()).replace(/\n+/g, ' | ').slice(0, 300));
  await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/order-detail.png' });
} else {
  console.log('no order link found');
}
console.log('errors:', errors.slice(0, 3).join(' || '));
await browser.close();
