import { chromium } from 'playwright-core';
const base = 'http://localhost:5180';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
await page.goto(base + '/login', { waitUntil: 'networkidle' });
await page.fill('input[type=email]', 'customer@kdshop.dev');
await page.fill('input[type=password]', 'Demo@123456');
await page.click('button.submit');
await page.waitForTimeout(1600);

await page.goto(base + '/products', { waitUntil: 'networkidle' });
await page.waitForTimeout(1200);
await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/ui-list.png' });

await page.goto(base + '/account', { waitUntil: 'networkidle' });
await page.waitForTimeout(1200);
await page.getByText('新增地址').click();
await page.waitForTimeout(800);
await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/ui-address-dialog.png' });

await page.goto(base + '/help', { waitUntil: 'networkidle' });
await page.waitForTimeout(800);
await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/ui-help.png' });
console.log('done');
await browser.close();
