import { chromium } from 'playwright-core';
const base = 'http://localhost:5180';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
page.on('response', async (r) => {
  if (r.url().includes('/follows') || r.url().includes('/shops/')) {
    let body = '';
    try { body = (await r.text()).slice(0, 200); } catch {}
    console.log('RESP', r.status(), r.request().method(), r.url().replace(base, ''), body);
  }
});
page.on('console', (m) => { if (m.type() === 'error') console.log('CONSOLE', m.text()); });

await page.goto(base + '/login', { waitUntil: 'networkidle' });
await page.fill('input[type=email]', 'customer@kdshop.dev');
await page.fill('input[type=password]', 'Demo@123456');
await page.click('button.submit');
await page.waitForTimeout(1800);
await page.goto(base + '/shops/1', { waitUntil: 'networkidle' });
await page.waitForTimeout(1200);
const btn = page.locator('.follow-btn');
console.log('button:', await btn.count(), await btn.innerText().catch(() => ''));
await btn.click();
await page.waitForTimeout(1500);
console.log('after click:', await btn.innerText().catch(() => ''));
await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/follow-debug.png' });
await browser.close();
