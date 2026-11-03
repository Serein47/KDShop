import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const seedDir = join(here, '../../db/seed');
const catalog = JSON.parse(readFileSync(join(seedDir, 'catalog.json'), 'utf8'));
const shops = JSON.parse(readFileSync(join(seedDir, 'shops.json'), 'utf8'));
const HASH = '$2b$10$sl74lctCfaCuSuQoPL44YeAV0paZnc5cRCHqxnseTe7lOKW6kDdue';

const out = [];
out.push('-- 演示数据：新增 5 个店铺与 90 个商品（products 31-120）');
out.push('-- 由 scripts/seed/generate-catalog-sql.mjs 从 db/seed/catalog.json 生成，勿手改');
out.push('');

for (const s of shops) {
  out.push(`INSERT INTO users (id, email, password_hash, nickname, status, email_verified_at) VALUES (${s.user}, 'merchant${s.id}@kdshop.dev', '${HASH}', '${s.nick}', 'ACTIVE', NOW(3));`);
  out.push(`INSERT INTO user_roles (user_id, role_id) VALUES (${s.user}, 1), (${s.user}, 2);`);
  out.push(`INSERT INTO merchants (id, user_id, shop_name, description, contact_name, contact_phone, status, commission_rate, approved_at) VALUES (${s.id}, ${s.user}, '${s.name}', '${s.desc}', '${s.nick}', '13800000${String(s.id).padStart(3, '0')}', 'ACTIVE', 500, NOW(3));`);
}
out.push('');

let skuId = 35;
for (const p of catalog.products) {
  const spec = JSON.stringify({ [p.specLabel]: p.specValue });
  const desc = `${p.subtitle}。演示数据，价格与库存均为虚构。`;
  out.push(`INSERT INTO products (id, merchant_id, category_id, name, subtitle, description, status, sales_count, sale_mode, deposit_rate, requires_flaw_ack) VALUES (${p.id}, ${p.shopId}, ${p.categoryId}, '${p.name}', '${p.subtitle}', '${desc}', 'ON', ${20 + ((p.id * 13) % 280)}, '${p.saleMode}', 0, 0);`);
  out.push(`INSERT INTO skus (id, product_id, spec_json, price_cent, original_price_cent, stock, status) VALUES (${skuId}, ${p.id}, '${spec}', ${p.price * 100}, ${p.price * 130}, ${p.stock}, 'ON');`);
  const tagIds = [...new Set(p.tags || [])];
  if (tagIds.length) {
    out.push(`INSERT INTO product_tags (product_id, tag_id) VALUES ${tagIds.map((t) => `(${p.id}, ${t})`).join(', ')};`);
  }
  skuId++;
}

out.push('');
const outDir = join(here, '../../backend/src/main/resources/db/seed');
const file = join(outDir, 'V9__seed_catalog_expanded.sql');
writeFileSync(file, out.join('\n'), 'utf8');
console.log('written', file, 'products', catalog.products.length, 'skus', skuId - 35);
