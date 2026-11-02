-- 把已上传的图片绑定到商品；key 存相对路径，渲染时拼 VITE_MEDIA_BASE
-- 当前有效：11, 14, 21, 23, 25, 29, 5

UPDATE products SET main_image='products/11/1.jpg', images_json=JSON_ARRAY('products/11/1.jpg') WHERE id=11;
UPDATE products SET main_image='products/14/1.jpg', images_json=JSON_ARRAY('products/14/1.jpg') WHERE id=14;
UPDATE products SET main_image='products/21/1.jpg', images_json=JSON_ARRAY('products/21/1.jpg') WHERE id=21;
UPDATE products SET main_image='products/23/1.jpg', images_json=JSON_ARRAY('products/23/1.jpg') WHERE id=23;
UPDATE products SET main_image='products/25/1.jpg', images_json=JSON_ARRAY('products/25/1.jpg') WHERE id=25;
UPDATE products SET main_image='products/29/1.jpg', images_json=JSON_ARRAY('products/29/1.jpg') WHERE id=29;
UPDATE products SET main_image='products/5/1.jpg', images_json=JSON_ARRAY('products/5/1.jpg') WHERE id=5;
UPDATE products SET main_image=NULL, images_json=NULL WHERE id=1;
UPDATE products SET main_image=NULL, images_json=NULL WHERE id=3;
UPDATE products SET main_image=NULL, images_json=NULL WHERE id=7;
