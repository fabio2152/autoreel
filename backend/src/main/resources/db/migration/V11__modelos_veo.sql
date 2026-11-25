-- Las tres versiones de Veo 3.1 en Google Cloud (2026-11-25), para compararlas desde el panel sin programar (RN-56).
-- Copian los parámetros del Veo 3.1 Lite que ya existe (proyecto, región, 720p, 9:16, sin audio, duraciones 4/6/8 s).
-- Costos referenciales por segundo a 720p, conservadores (con audio): Fast 0,10 y estándar 0,40 dólares; sin audio
-- Google cobra menos. Se ajustan desde el panel con la factura real.
insert into modelo_ia (nombre, proveedor, tipo, adaptador, identificador_modelo, parametros,
                       costo_referencial, unidad_costo, nombre_secreto, estado)
select nuevo.nombre, lite.proveedor, lite.tipo, lite.adaptador, nuevo.identificador, lite.parametros,
       nuevo.costo, lite.unidad_costo, lite.nombre_secreto, 'ACTIVO'
from modelo_ia lite
cross join (values ('Veo 3.1 Fast', 'veo-3.1-fast-generate-001', 0.100000),
                   ('Veo 3.1', 'veo-3.1-generate-001', 0.400000)) as nuevo (nombre, identificador, costo)
where lite.adaptador = 'VEO' and lite.identificador_modelo = 'veo-3.1-lite-generate-001'
  and not exists (select 1 from modelo_ia ya
                  where ya.adaptador = 'VEO' and ya.identificador_modelo = nuevo.identificador);

-- Decisión del usuario (2026-11-25): los videos se hacen con Veo en Google Cloud, empezando por Veo 3.1 Lite.
-- Gemini Omni Flash queda registrado pero sin plan.
update modelo_ia set estado = 'ACTIVO'
where adaptador = 'VEO' and identificador_modelo = 'veo-3.1-lite-generate-001';
update plan
set modelo_video_id = (select id from modelo_ia
                       where adaptador = 'VEO' and identificador_modelo = 'veo-3.1-lite-generate-001')
where nombre = 'Lite';
