import { chromium } from 'playwright-core';
const base = 'http://localhost:5180';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
await page.goto(base + '/login', { waitUntil: 'networkidle' });
await page.fill('input[type=email]', 'customer@kdshop.dev');
await page.fill('input[type=password]', 'Demo@123456');
await page.click('button.submit');
await page.waitForTimeout(1800);

await page.goto(base + '/products/1', { waitUntil: 'networkidle' });
await page.waitForTimeout(1200);
await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
await page.waitForTimeout(800);
await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/product-reviews.png' });

await page.goto(base + '/orders/KD202610010001', { waitUntil: 'networkidle' });
await page.waitForTimeout(1400);
await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/order-review-entry.png' });

await page.goto(base + '/login', { waitUntil: 'networkidle' });
await page.fill('input[type=email]', 'merchant1@kdshop.dev');
await page.fill('input[type=password]', 'Demo@123456');
await page.click('button.submit');
await page.waitForTimeout(1800);
await page.hover('text=商家中心');
await page.waitForTimeout(600);
await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/merchant-menu.png' });
console.log('ok');
await browser.close();
