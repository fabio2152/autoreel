-- Beneficios por cantidad de usos (2026-11-25): el administrador da otro plan por una cantidad de videos (1 a 30), no
-- por un rango de fechas. Un uso es un video creado con el beneficio y aprobado ("Crear mi video"); si ese video
-- falla al generarse o al armarse, el uso se devuelve junto con el video del día (RN-27).

alter table beneficio_temporal add column usos_otorgados integer;
alter table beneficio_temporal add column usos_consumidos integer not null default 0;
alter table beneficio_temporal add column terminado boolean not null default false;

-- Los beneficios por fechas que ya existen quedan en el historial como terminados; conservan sus fechas.
update beneficio_temporal set usos_otorgados = 1, terminado = true;
alter table beneficio_temporal alter column usos_otorgados set not null;

alter table beneficio_temporal drop constraint beneficio_rango_ck;
alter table beneficio_temporal drop constraint beneficio_maximo_ck;
alter table beneficio_temporal alter column desde drop not null;
alter table beneficio_temporal alter column hasta drop not null;

alter table beneficio_temporal add constraint beneficio_usos_ck
    check (usos_otorgados between 1 and 30 and usos_consumidos between 0 and usos_otorgados);

-- El beneficio cuyo plan usa el video, para descontar y devolver sus usos. Vacío si el video usa el plan de la
-- suscripción.
alter table video add column beneficio_id bigint references beneficio_temporal (id);
