-- Correcciones de un plano (2026-11-25): el negocio escribe lo que no quiere ver en la toma ("que no tenga hojas de
-- menta"). Antes de generar o regenerar el clip, el modelo de texto reescribe la toma con ellas, una sola vez.
alter table plano add column correcciones varchar(300);

-- Si la toma actual ya se reescribió con las correcciones guardadas; se vuelve a false cuando cambian.
alter table plano add column correcciones_aplicadas boolean not null default false;
