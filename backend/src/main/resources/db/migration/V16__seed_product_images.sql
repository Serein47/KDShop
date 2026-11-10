-- 种子商品默认图：演示素材按 products/{id}/1.jpg 组织
-- 仅补齐缺失值，已有绑定（本地 bind-media 脚本）不受影响
UPDATE products
SET main_image = CONCAT('products/', id, '/1.jpg'),
    images_json = JSON_ARRAY(CONCAT('products/', id, '/1.jpg'))
WHERE main_image IS NULL
  AND id <= 120;
