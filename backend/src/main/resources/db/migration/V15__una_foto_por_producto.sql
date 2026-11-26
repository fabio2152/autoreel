-- Una sola foto por producto (2026-11-25): la IA trabaja mejor con una sola foto. Queda solo "FOTO_1".

-- Un producto sin FOTO_1 pero con FOTO_2 o FOTO_3 no pierde su foto: la de menor número pasa a ser la FOTO_1.
update foto_producto f set tipo = 'FOTO_1'
    where f.tipo in ('FOTO_2', 'FOTO_3')
      and not exists (select 1 from foto_producto p where p.producto_id = f.producto_id and p.tipo = 'FOTO_1')
      and f.tipo = (select min(q.tipo) from foto_producto q where q.producto_id = f.producto_id);

-- Las demás fotos se borran. Sus objetos en S3 no se borran: los planos de los videos ya hechos guardan su clave.
delete from foto_producto where tipo in ('FOTO_2', 'FOTO_3');

alter table foto_producto drop constraint foto_producto_tipo_ck;
alter table foto_producto add constraint foto_producto_tipo_ck check (tipo = 'FOTO_1');

-- Los planos de videos anteriores que usaban la foto 2 o 3 conservan su clave_foto y quedan sin tipo ("otra foto").
update plano set tipo_foto = null where tipo_foto in ('FOTO_2', 'FOTO_3');

alter table plano drop constraint plano_tipo_foto_ck;
alter table plano add constraint plano_tipo_foto_ck check (tipo_foto is null or tipo_foto = 'FOTO_1');
