-- Fase 1: modelos de IA, planes y suscripciones (secciones 9.1, 10.8, 11.5, 15.2 y 16).
-- Los planes y los modelos viven en la base de datos: agregar uno nuevo no requiere programar.

create table modelo_ia (
    id                   bigint generated always as identity primary key,
    nombre               varchar(80)   not null,
    proveedor            varchar(30)   not null,
    tipo                 varchar(10)   not null,
    adaptador            varchar(30)   not null,
    identificador_modelo varchar(120)  not null,
    parametros           jsonb         not null default '{}'::jsonb,
    costo_referencial    numeric(12, 6) not null,
    unidad_costo         varchar(20)   not null,
    nombre_secreto       varchar(120)  not null,
    estado               varchar(10)   not null default 'ACTIVO',
    ultima_prueba        timestamptz,
    constraint modelo_ia_tipo_ck check (tipo in ('TEXTO', 'VIDEO')),
    constraint modelo_ia_unidad_costo_ck check (unidad_costo in ('POR_SEGUNDO', 'POR_SOLICITUD')),
    constraint modelo_ia_estado_ck check (estado in ('ACTIVO', 'INACTIVO')),
    constraint modelo_ia_costo_ck check (costo_referencial >= 0)
);

create unique index modelo_ia_adaptador_identificador_uk on modelo_ia (adaptador, identificador_modelo);

create table plan (
    id                             bigint generated always as identity primary key,
    nombre                         varchar(40)  not null,
    descripcion                    varchar(300),
    videos_por_dia                 integer      not null,
    regeneraciones_por_video       integer      not null,
    modelo_texto_id                bigint       not null references modelo_ia (id),
    modelo_video_id                bigint       not null references modelo_ia (id),
    opciones_produccion_permitidas jsonb        not null,
    permite_personas               boolean      not null default false,
    precio_referencial             numeric(10, 2),
    activo                         boolean      not null default true,
    constraint plan_nombre_uk unique (nombre),
    constraint plan_videos_por_dia_ck check (videos_por_dia > 0),
    constraint plan_regeneraciones_ck check (regeneraciones_por_video >= 0)
);

create table suscripcion (
    id                 bigint generated always as identity primary key,
    negocio_id         bigint         not null references negocio (id),
    plan_id            bigint         not null references plan (id),
    monto_mensual      numeric(10, 2) not null,
    fecha_inicio       date           not null,
    proxima_fecha_pago date           not null,
    dias_aviso         integer        not null default 5,
    constraint suscripcion_negocio_uk unique (negocio_id),
    constraint suscripcion_monto_ck check (monto_mensual > 0),
    constraint suscripcion_dias_aviso_ck check (dias_aviso between 0 and 30)
);

-- Datos iniciales (sección 16). Las credenciales nunca se guardan aquí: solo el nombre del secreto (RN-55).
insert into modelo_ia (nombre, proveedor, tipo, adaptador, identificador_modelo, parametros,
                       costo_referencial, unidad_costo, nombre_secreto)
values ('Gemini', 'GOOGLE', 'TEXTO', 'GEMINI', 'gemini-3.8-flash',
        '{"temperatura": 0.7, "formatoRespuesta": "JSON"}',
        0.010000, 'POR_SOLICITUD', 'autoreel/gemini'),
       ('Veo 3.1 Lite', 'GOOGLE', 'VIDEO', 'VEO', 'veo-3.1-lite-generate-001',
        '{"resolucion": "720p", "duracionClipSegundos": 4, "relacionAspecto": "9:16"}',
        0.050000, 'POR_SEGUNDO', 'autoreel/gemini');

insert into plan (nombre, descripcion, videos_por_dia, regeneraciones_por_video,
                  modelo_texto_id, modelo_video_id, opciones_produccion_permitidas, permite_personas)
values ('Lite', 'Un video por día con los modelos más económicos.', 1, 3,
        (select id from modelo_ia where adaptador = 'GEMINI' and identificador_modelo = 'gemini-3.8-flash'),
        (select id from modelo_ia where adaptador = 'VEO' and identificador_modelo = 'veo-3.1-lite-generate-001'),
        '{"musica": true, "textoEnPantalla": "COMPLETO", "vozEnOff": false, "escenas": "SOLO_PRODUCTO", "personas": "NINGUNA"}',
        false);
