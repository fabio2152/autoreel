-- Fase 2: perfil del negocio (onboarding) y catálogo de productos (secciones 7.1, 7.2 y 9.1).
-- Los archivos se guardan como claves del almacén (bucket privado), no como URLs: las URLs se firman al leerlas.

create table perfil_negocio (
    id                  bigint generated always as identity primary key,
    negocio_id          bigint      not null references negocio (id),
    logo_clave          varchar(300),
    logo_sin_transparencia boolean  not null default false,
    color_primario      varchar(7),
    color_secundario    varchar(7),
    estilo              varchar(30),
    foto_local_clave    varchar(300),
    -- Respuestas del onboarding, validadas contra las preguntas del paquete de nicho.
    respuestas          jsonb       not null default '{}'::jsonb,
    fecha_actualizacion timestamptz not null default now(),
    constraint perfil_negocio_negocio_uk unique (negocio_id),
    constraint perfil_negocio_color_primario_ck check (color_primario ~ '^#[0-9a-f]{6}$'),
    constraint perfil_negocio_color_secundario_ck check (color_secundario ~ '^#[0-9a-f]{6}$')
);

-- Los negocios creados en la fase 1 reciben su perfil vacío.
insert into perfil_negocio (negocio_id) select id from negocio;

create table producto (
    id             bigint generated always as identity primary key,
    negocio_id     bigint       not null references negocio (id),
    nombre         varchar(50)  not null,
    descripcion    varchar(200) not null,
    -- Ids de temporadas del paquete de nicho (por ejemplo, ["VERANO"]).
    temporadas     jsonb        not null,
    estado         varchar(20)  not null default 'ACTIVO',
    fecha_creacion timestamptz  not null default now(),
    constraint producto_estado_ck check (estado in ('ACTIVO', 'ARCHIVADO'))
);

create index producto_negocio_idx on producto (negocio_id, estado);

create table foto_producto (
    id          bigint generated always as identity primary key,
    producto_id bigint       not null references producto (id) on delete cascade,
    tipo        varchar(20)  not null,
    clave_s3    varchar(300) not null,
    -- Medidas tal como se ve la foto (con la orientación de la cámara aplicada).
    ancho       integer      not null,
    alto        integer      not null,
    constraint foto_producto_tipo_ck check (tipo in ('ENTERO', 'DETALLE', 'PORCION')),
    constraint foto_producto_producto_tipo_uk unique (producto_id, tipo)
);
