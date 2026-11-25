-- Modos de tomas (2026-11-25): el negocio elige en el chat cómo quiere las tomas (cortas, largas con transiciones,
-- historia, plano único o plano extendido). Vacío en los videos anteriores: usan el modo por defecto del paquete.
alter table video add column modo_tomas varchar(20);

-- Plano extendido: el clip ya generado se está extendiendo con Veo. Si la extensión falla, el plano conserva el clip
-- base.
alter table plano add column extension_en_curso boolean not null default false;
