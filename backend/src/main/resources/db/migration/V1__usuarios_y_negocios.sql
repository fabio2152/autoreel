-- Fase 1: negocios y usuarios (secciones 9.1 y 10.1).

create table negocio (
    id                  bigint generated always as identity primary key,
    nombre              varchar(120) not null,
    nicho               varchar(40)  not null,
    estado              varchar(20)  not null default 'ACTIVO',
    onboarding_completo boolean      not null default false,
    fecha_creacion      timestamptz  not null default now(),
    constraint negocio_estado_ck check (estado in ('ACTIVO', 'SUSPENDIDO'))
);

create table usuario (
    id                        bigint generated always as identity primary key,
    correo                    varchar(254) not null,
    contrasena_hash           varchar(100) not null,
    rol                       varchar(20)  not null,
    negocio_id                bigint references negocio (id),
    debe_cambiar_contrasena   boolean      not null default false,
    contrasena_temporal_vence timestamptz,
    activo                    boolean      not null default true,
    ultimo_acceso             timestamptz,
    fecha_creacion            timestamptz  not null default now(),
    version_sesion            integer      not null default 0,
    intentos_fallidos         integer      not null default 0,
    bloqueado_hasta           timestamptz,
    constraint usuario_rol_ck check (rol in ('ADMIN', 'NEGOCIO')),
    constraint usuario_rol_negocio_ck check (
        (rol = 'NEGOCIO' and negocio_id is not null) or (rol = 'ADMIN' and negocio_id is null)
    )
);

-- RN-01: el correo es único en toda la plataforma y cada negocio tiene un solo usuario.
create unique index usuario_correo_uk on usuario (lower(correo));
create unique index usuario_negocio_uk on usuario (negocio_id);
