-- Fase 3: chat del video del día (secciones 7.3, 9.1 y 10.3).

create table conversacion (
    id                  bigint generated always as identity primary key,
    negocio_id          bigint      not null references negocio (id),
    estado              varchar(20) not null default 'EN_CURSO',
    -- Paso del guion del chat que espera respuesta; vacío cuando la conversación terminó.
    paso_actual         varchar(40),
    -- Respuestas reunidas: la entrada del guion de la fase 4.
    respuestas          jsonb       not null default '{}'::jsonb,
    fecha_inicio        timestamptz not null default now(),
    fecha_actualizacion timestamptz not null default now(),
    constraint conversacion_estado_ck check (estado in ('EN_CURSO', 'COMPLETA', 'ABANDONADA'))
);

-- RN-19: cada negocio tiene como máximo una conversación en curso.
create unique index conversacion_en_curso_uk on conversacion (negocio_id) where estado = 'EN_CURSO';
create index conversacion_negocio_idx on conversacion (negocio_id, estado, fecha_actualizacion desc);

create table mensaje_chat (
    id              bigint generated always as identity primary key,
    conversacion_id bigint      not null references conversacion (id) on delete cascade,
    autor           varchar(10) not null,
    texto           text        not null,
    fecha_hora      timestamptz not null,
    -- Los textos del usuario quedan pendientes hasta agruparse en una respuesta (RN-17).
    procesado       boolean     not null default true,
    constraint mensaje_chat_autor_ck check (autor in ('SISTEMA', 'USUARIO'))
);

create index mensaje_chat_conversacion_idx on mensaje_chat (conversacion_id, fecha_hora, id);

-- RN-14: un producto subido desde el chat sin aceptar guardarlo no aparece en el catálogo.
alter table producto add column en_catalogo boolean not null default true;
