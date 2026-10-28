-- 修正 V5 未覆盖的商品描述（原咖啡/茶文案）
UPDATE products SET description='<p>手工拉坯，130ml，可进洗碗机。</p>' WHERE id=1;
UPDATE products SET description='<p>水洗亚麻，45×30cm，可机洗。</p>' WHERE id=2;
UPDATE products SET description='<p>素坯花器，高 18cm，可插干花或鲜花。</p>' WHERE id=7;
UPDATE products SET description='<p>胡桃木盒，内衬绒布，盒面可刻名字或一句话。</p>' WHERE id=9;
UPDATE products SET description='<p>手工打结羊毛地毯，1.2×1.8m，背面防滑。</p>' WHERE id=19;
