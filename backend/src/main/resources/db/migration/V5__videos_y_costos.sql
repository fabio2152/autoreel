-- Fase 4: video, planos del storyboard y registro de costos (secciones 7.4, 9.1, 9.2 y 10.4).

create table video (
    id                     bigint generated always as identity primary key,
    negocio_id             bigint         not null references negocio (id),
    -- Un video por conversación completa (la relación de 9.1 se guarda en el video).
    conversacion_id        bigint         not null references conversacion (id),
    producto_id            bigint         not null references producto (id),
    -- Valor del paquete de nicho (regla 5): sin CHECK.
    tipo_video             varchar(40)    not null,
    estado                 varchar(20)    not null default 'GUION_PENDIENTE',
    -- RN-54: plan y modelos con los que se creó; un cambio de plan no afecta a un video en proceso.
    plan_aplicado_id       bigint         not null references plan (id),
    modelo_texto_usado_id  bigint         not null references modelo_ia (id),
    modelo_video_usado_id  bigint         not null references modelo_ia (id),
    opciones_produccion    jsonb          not null default '{}'::jsonb,
    -- Última respuesta del modelo, solo para auditoría: las filas de "plano" son la fuente de verdad.
    guion                  jsonb,
    descripcion_publicacion text,
    hashtags               jsonb          not null default '[]'::jsonb,
    guion_de_respaldo      boolean        not null default false,
    motivo_respaldo        varchar(30),
    intentos_guion         integer        not null default 0,
    costo_estimado         numeric(12, 6) not null default 0,
    -- Reclamo del procesador de guiones: evita que dos ejecuciones tomen el mismo video.
    guion_reclamado_hasta  timestamptz,
    motivo_error           varchar(300),
    version                integer        not null default 0,
    fecha_creacion         timestamptz    not null,
    fecha_actualizacion    timestamptz    not null,
    constraint video_conversacion_uk unique (conversacion_id),
    constraint video_estado_ck check (estado in ('GUION_PENDIENTE', 'STORYBOARD', 'GENERANDO', 'COMPONIENDO',
        'LISTO', 'APROBADO', 'PROGRAMADO', 'PUBLICADO', 'ERROR', 'DESCARTADO')),
    constraint video_motivo_respaldo_ck check (motivo_respaldo is null or motivo_respaldo in ('SIN_CREDENCIAL',
        'CREDENCIAL_INVALIDA', 'CONFIGURACION_INVALIDA', 'PROVEEDOR_NO_DISPONIBLE', 'GUION_INVALIDO',
        'MODELO_INACTIVO', 'PROCESO_INTERRUMPIDO')),
    constraint video_costo_ck check (costo_estimado >= 0)
);

create index video_negocio_idx on video (negocio_id, fecha_creacion desc);
create index video_guion_pendiente_idx on video (fecha_creacion) where estado = 'GUION_PENDIENTE';

create table plano (
    id         bigint generated always as identity primary key,
    video_id   bigint       not null references video (id) on delete cascade,
    orden      integer      not null,
    -- Foto del producto que usa; vacío en el cierre o si el usuario subió otra en el storyboard.
    tipo_foto  varchar(10),
    clave_foto varchar(300),
    movimiento varchar(40),
    texto      varchar(200),
    -- Cierre (plano 4): lo arma el sistema, sin IA.
    precio     varchar(40),
    llamado    varchar(120),
    ubicacion  varchar(120),
    constraint plano_video_orden_uk unique (video_id, orden),
    constraint plano_orden_ck check (orden between 1 and 4),
    constraint plano_foto_ck check (orden = 4 or clave_foto is not null),
    constraint plano_tipo_foto_ck check (tipo_foto is null or tipo_foto in ('ENTERO', 'DETALLE', 'PORCION'))
);

-- RN-32: cada llamada a un proveedor de IA registra su costo.
create table registro_costo (
    id         bigint generated always as identity primary key,
    negocio_id bigint         not null references negocio (id),
    -- Vacío en llamadas que no son de un video (por ejemplo, la prueba de conexión de la fase 7).
    video_id   bigint references video (id),
    proveedor  varchar(30)    not null,
    concepto   varchar(20)    not null,
    cantidad   numeric(12, 3) not null,
    costo_usd  numeric(12, 6) not null,
    fecha_hora timestamptz    not null,
    constraint registro_costo_concepto_ck check (concepto in ('GUION', 'CLIP', 'PRUEBA')),
    constraint registro_costo_costo_ck check (costo_usd >= 0)
);

create index registro_costo_negocio_idx on registro_costo (negocio_id, fecha_hora);
create index registro_costo_video_idx on registro_costo (video_id);
