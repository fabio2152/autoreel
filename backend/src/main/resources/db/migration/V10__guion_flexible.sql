-- Guion flexible (2026-11-25): la IA ve las fotos del producto, elige la estructura del video (cuántas tomas y de
-- cuántos segundos), el nivel de creatividad y escribe la toma de cámara de cada plano. Ver el ADR 0022.

-- Fotos del producto: de "entero, detalle y porción" obligatorias a "Foto 1" obligatoria y "Foto 2" y "Foto 3"
-- opcionales, de cualquier ángulo.
alter table foto_producto drop constraint foto_producto_tipo_ck;
update foto_producto set tipo = case tipo when 'ENTERO' then 'FOTO_1' when 'DETALLE' then 'FOTO_2' else 'FOTO_3' end;
alter table foto_producto add constraint foto_producto_tipo_ck check (tipo in ('FOTO_1', 'FOTO_2', 'FOTO_3'));

alter table plano drop constraint plano_tipo_foto_ck;
update plano set tipo_foto = case tipo_foto when 'ENTERO' then 'FOTO_1' when 'DETALLE' then 'FOTO_2' else 'FOTO_3' end
    where tipo_foto is not null;
alter table plano add constraint plano_tipo_foto_ck check (tipo_foto is null or tipo_foto in ('FOTO_1', 'FOTO_2', 'FOTO_3'));

-- De 1 a 4 tomas más el cierre: el cierre ya no es siempre el plano 4 (es el que no tiene foto).
alter table plano drop constraint plano_orden_ck;
alter table plano add constraint plano_orden_ck check (orden between 1 and 5);
alter table plano drop constraint plano_foto_ck;

-- Cada plano guarda su duración. La toma (en inglés, para el modelo de video) y su descripción (en español, para el
-- storyboard) las escribe la IA; en los videos anteriores, la toma sale del movimiento del catálogo.
alter table plano add column segundos integer;
alter table plano add column toma varchar(1500);
alter table plano add column descripcion_toma varchar(160);
update plano set segundos = case orden when 1 then 3 else 4 end;
alter table plano alter column segundos set not null;
alter table plano add constraint plano_segundos_ck check (segundos between 1 and 15);

-- Lo que eligió la IA para el video: la estructura, el nivel de creatividad y la idea en una frase.
alter table video add column estructura varchar(20);
alter table video add column creatividad varchar(20);
alter table video add column idea varchar(400);

-- Veo admite clips de 4, 6 u 8 segundos: cada toma pide la duración que necesita.
update modelo_ia set parametros = parametros || '{"duracionesClip": [4, 6, 8]}'::jsonb where adaptador = 'VEO';

-- Los videos anteriores tenían la estructura de 3 tomas (3, 4 y 4 s) y el cierre de 4 s.
update video set estructura = 'CLASICA' where exists (select 1 from plano where plano.video_id = video.id);
