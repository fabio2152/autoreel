-- Mejora de la calidad del video (2026-11-12): el cierre lo propone la IA.

-- Texto principal del cierre cuando no hay precio: una frase gancho escrita por la IA ("Recién salidas del horno").
-- El precio solo va si el negocio escribió uno de verdad.
alter table plano add column frase varchar(60);

-- Sin guion de respaldo: si el modelo de texto falla, el guion se reintenta solo durante unos minutos desde este
-- momento (la creación del video o el último "Reintentar"). Los videos anteriores usan su fecha de creación.
-- Las columnas guion_de_respaldo y motivo_respaldo de video quedan solo para los videos creados antes.
alter table video add column guion_iniciado_en timestamptz;

-- Un error que no se arregla reintentando (por ejemplo, el producto del video ya no tiene fotos): no se ofrece
-- "Reintentar" para no volver a pagar el guion sin resultado.
alter table video add column error_definitivo boolean not null default false;
