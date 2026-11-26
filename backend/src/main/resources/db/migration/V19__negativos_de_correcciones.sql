-- Negativos de las correcciones (2026-11-25): lo que el negocio no quiere ver va como prompt negativo del modelo de
-- video, en inglés ("flowers, floral decor"). Nombrarlo en la toma, aunque sea para negarlo, hace que Veo lo dibuje.
-- Lo escribe el modelo de texto al reescribir la toma; vacío si el plano no tiene correcciones aplicadas.
alter table plano add column negativos_correccion varchar(300);
