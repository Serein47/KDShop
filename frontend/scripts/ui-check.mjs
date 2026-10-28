// 用本机 Edge 做一次端到端 UI 冒烟：登录 → 加购 → 看购物车，并截图。
// 用法：先起后端(8081)与前端(5180)，然后 node scripts/ui-check.mjs
import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';

const base = process.env.UI_BASE ?? 'http://localhost:5180';
const shots = new URL('../../.shots/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
mkdirSync(shots, { recursive: true });

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 950 } });

await page.goto(base + '/login', { waitUntil: 'networkidle' });
await page.click('button.submit');
await page.waitForTimeout(1200);

await page.goto(base + '/products/1', { waitUntil: 'networkidle' });
const ack = page.locator('.ack input');
if (await ack.count()) await ack.check();
await page.click('button.cta');
await page.waitForTimeout(800);

await page.goto(base + '/cart', { waitUntil: 'networkidle' });
await page.waitForTimeout(800);
await page.screenshot({ path: shots + 'cart.png' });

console.log('cart rows:', await page.locator('.row').count());
console.log('total:', await page.locator('.total strong').innerText().catch(() => 'n/a'));
await browser.close();
