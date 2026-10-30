import { chromium } from 'playwright-core';
const base = 'http://localhost:5180';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });

page.on('response', async (r) => {
  if (r.url().includes('/checkout/preview')) {
    console.log('preview:', r.status());
  }
});

await page.goto(base + '/login', { waitUntil: 'networkidle' });
await page.click('button.submit');
await page.waitForTimeout(1500);

await page.goto(base + '/cart', { waitUntil: 'networkidle' });
await page.waitForTimeout(1200);
await page.getByText('去结算').click();
await page.waitForTimeout(2000);
console.log('url:', page.url());
await page.screenshot({ path: 'D:/web-ai-code/KDShop/.shots/checkout.png', fullPage: false });
await browser.close();
