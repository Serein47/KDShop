import { chromium } from 'playwright-core';
const base = 'http://localhost:5180';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
await page.goto(base + '/login', { waitUntil: 'networkidle' });
await page.fill('input[type=email]', 'admin@kdshop.dev');
await page.fill('input[type=password]', 'Demo@123456');
await page.click('button.submit');
await page.waitForTimeout(1800);

await page.goto(base + '/admin/orders', { waitUntil: 'networkidle' });
await page.waitForTimeout(1200);
await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/admin-orders.png' });

await page.goto(base + '/admin/refunds', { waitUntil: 'networkidle' });
await page.waitForTimeout(1200);
await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/admin-refunds.png' });
console.log('ok');
await browser.close();
