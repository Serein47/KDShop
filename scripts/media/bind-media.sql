-- 把已上传的图片绑定到商品；key 存相对路径，渲染时拼 MEDIA_PUBLIC_BASE
-- 试点商品：1,3,5,7,11,14,21,23,25,29
UPDATE products SET
  main_image = 'products/1/1.jpg',
  images_json = JSON_ARRAY('products/1/1.jpg','products/1/2.jpg','products/1/3.jpg')
WHERE id = 1;

UPDATE products SET
  main_image = 'products/3/1.jpg',
  images_json = JSON_ARRAY('products/3/1.jpg','products/3/2.jpg','products/3/3.jpg')
WHERE id = 3;

UPDATE products SET
  main_image = 'products/5/1.jpg',
  images_json = JSON_ARRAY('products/5/1.jpg','products/5/2.jpg','products/5/3.jpg')
WHERE id = 5;

UPDATE products SET
  main_image = 'products/7/1.jpg',
  images_json = JSON_ARRAY('products/7/1.jpg','products/7/2.jpg','products/7/3.jpg')
WHERE id = 7;

UPDATE products SET
  main_image = 'products/11/1.jpg',
  images_json = JSON_ARRAY('products/11/1.jpg','products/11/2.jpg','products/11/3.jpg')
WHERE id = 11;

UPDATE products SET
  main_image = 'products/14/1.jpg',
  images_json = JSON_ARRAY('products/14/1.jpg','products/14/2.jpg','products/14/3.jpg')
WHERE id = 14;

UPDATE products SET
  main_image = 'products/21/1.jpg',
  images_json = JSON_ARRAY('products/21/1.jpg','products/21/2.jpg','products/21/3.jpg')
WHERE id = 21;

UPDATE products SET
  main_image = 'products/23/1.jpg',
  images_json = JSON_ARRAY('products/23/1.jpg','products/23/2.jpg','products/23/3.jpg')
WHERE id = 23;

UPDATE products SET
  main_image = 'products/25/1.jpg',
  images_json = JSON_ARRAY('products/25/1.jpg','products/25/2.jpg','products/25/3.jpg')
WHERE id = 25;

UPDATE products SET
  main_image = 'products/29/1.jpg',
  images_json = JSON_ARRAY('products/29/1.jpg','products/29/2.jpg','products/29/3.jpg')
WHERE id = 29;
