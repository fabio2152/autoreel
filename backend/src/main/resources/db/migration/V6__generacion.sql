-- Fase 5: generación de clips, composición, revisión y consumo diario (secciones 7.4, 8.2 a 8.5, 9.1 y 10.5).

-- Biblioteca de música por nicho y estilo. La carga el paquete de nicho al arrancar (BibliotecaMusical).
create table pista_musical (
    id                bigint generated always as identity primary key,
    nicho             varchar(40)  not null,
    estilo            varchar(40)  not null,
    nombre            varchar(120) not null,
    clave             varchar(300) not null,
    duracion_segundos integer      not null,
    licencia          varchar(300) not null,
    activa            boolean      not null default true,
    constraint pista_musical_clave_uk unique (clave),
    constraint pista_musical_duracion_ck check (duracion_segundos > 0)
);

create index pista_musical_estilo_idx on pista_musical (nicho, estilo) where activa;

-- RN-26: videos aprobados por día de Lima.
create table consumo_diario (
    id               bigint generated always as identity primary key,
    negocio_id       bigint  not null references negocio (id),
    fecha            date    not null,
    videos_generados integer not null default 0,
    constraint consumo_diario_negocio_fecha_uk unique (negocio_id, fecha),
    constraint consumo_diario_videos_ck check (videos_generados >= 0)
);

alter table video
    add column consumo_fecha           date,
    add column consumo_devuelto        boolean      not null default false,
    add column regeneraciones_usadas   integer      not null default 0,
    add column intentos_composicion    integer      not null default 0,
    -- Reclamo del procesador de videos (generación y composición).
    add column trabajo_reclamado_hasta timestamptz,
    add column generacion_iniciada_en  timestamptz,
    add column clave_final             varchar(300),
    -- Aviso para el usuario cuando una recomposición no se pudo aplicar.
    add column aviso_revision          varchar(300),
    add column pista_musical_id        bigint references pista_musical (id),
    add constraint video_regeneraciones_ck check (regeneraciones_usadas >= 0),
    add constraint video_intentos_composicion_ck check (intentos_composicion >= 0);

create index video_en_proceso_idx on video (fecha_actualizacion) where estado in ('GENERANDO', 'COMPONIENDO');

alter table plano
    -- Vacío en el storyboard y en el cierre, que no pasan por la IA.
    add column estado                  varchar(12),
    add column intentos                integer      not null default 0,
    add column uso_respaldo            boolean      not null default false,
    add column motivo_respaldo         varchar(30),
    add column id_tarea_proveedor      varchar(500),
    add column enviado_en              timestamptz,
    add column clave_clip              varchar(300),
    -- Una regeneración pedida y todavía sin resultado: si termina en respaldo por el proveedor, se devuelve.
    add column regeneracion_en_curso   boolean      not null default false,
    add constraint plano_estado_ck check (estado is null or estado in ('PENDIENTE', 'GENERANDO', 'LISTO', 'RESPALDO')),
    add constraint plano_intentos_ck check (intentos >= 0),
    add constraint plano_motivo_respaldo_ck check (motivo_respaldo is null or motivo_respaldo in ('SIN_CREDENCIAL',
        'CREDENCIAL_INVALIDA', 'CONFIGURACION_INVALIDA', 'PROVEEDOR_NO_DISPONIBLE', 'CLIP_FALLIDO',
        'TIEMPO_AGOTADO', 'MODELO_INACTIVO'));

-- Modelo de video en Google Cloud (Vertex AI): credencial propia (cuenta de servicio) y sus parámetros.
update modelo_ia
set nombre_secreto = 'autoreel/veo',
    parametros     = '{"region": "us-central1", "duracionClipSegundos": 4, "relacionAspecto": "9:16", "resolucion": "720p", "generarAudio": false, "ajuste": "crop"}'
where adaptador = 'VEO' and identificador_modelo = 'veo-3.1-lite-generate-001';
