-- Veo empieza cada clip con la foto casi quieta y luego salta a su animación (2026-11-25). La composición salta el
-- primer segundo de cada clip y, para que la toma siga durando lo mismo, se pide a Veo un clip más largo (6 s de toma
-- → clip de 8 s). El valor vive en los parámetros del modelo: otro proveedor puede no necesitarlo.
update modelo_ia set parametros = parametros || '{"recorteInicialSegundos": 1.0}'::jsonb where adaptador = 'VEO';
