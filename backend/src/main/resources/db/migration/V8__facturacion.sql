-- Fase 7: pagos, beneficios temporales y notificaciones del administrador (secciones 9.1, 10.8, 10.9 y 10.10).

-- RN-42: al avanzar la fecha de pago se conserva el día del primer pago; si el mes no lo tiene, se usa el último día.
alter table suscripcion add column dia_pago integer;
update suscripcion set dia_pago = extract(day from proxima_fecha_pago);
alter table suscripcion alter column dia_pago set not null;
alter table suscripcion add constraint suscripcion_dia_pago_ck check (dia_pago between 1 and 31);

-- RF-08: cada pago cubre una fecha de pago (periodo_cubierto) y la hace avanzar un mes.
create table pago (
    id               bigint generated always as identity primary key,
    suscripcion_id   bigint         not null references suscripcion (id),
    fecha_pago       date           not null,
    monto            numeric(10, 2) not null,
    medio            varchar(20)    not null,
    periodo_cubierto date           not null,
    nota             varchar(300),
    registrado_por   bigint         not null references usuario (id),
    fecha_registro   timestamptz    not null,
    constraint pago_monto_ck check (monto > 0),
    constraint pago_medio_ck check (medio in ('TRANSFERENCIA', 'YAPE', 'PLIN', 'EFECTIVO', 'OTRO')),
    -- Dos clics seguidos en "Registrar pago" no pagan dos veces el mismo período.
    constraint pago_periodo_uk unique (suscripcion_id, periodo_cubierto)
);

-- RF-12 y RN-51: un plan superior durante un período, con motivo y como máximo 30 días.
create table beneficio_temporal (
    id             bigint generated always as identity primary key,
    negocio_id     bigint       not null references negocio (id),
    plan_id        bigint       not null references plan (id),
    desde          timestamptz  not null,
    hasta          timestamptz  not null,
    motivo         varchar(300) not null,
    otorgado_por   bigint       not null references usuario (id),
    fecha_creacion timestamptz  not null,
    constraint beneficio_rango_ck check (hasta > desde),
    constraint beneficio_maximo_ck check (hasta <= desde + interval '30 days')
);

create index beneficio_negocio_idx on beneficio_temporal (negocio_id, hasta desc);

-- RN-44, RN-45 y RN-57. "periodo" es la fecha de pago en las de pago y el día en las de integración: nunca se repite
-- el mismo tipo para el mismo negocio (o modelo) y el mismo período.
create table notificacion_admin (
    id           bigint generated always as identity primary key,
    tipo         varchar(30)  not null,
    negocio_id   bigint references negocio (id),
    modelo_ia_id bigint references modelo_ia (id),
    periodo      date         not null,
    mensaje      varchar(300) not null,
    fecha        timestamptz  not null,
    leida        boolean      not null default false,
    constraint notificacion_tipo_ck check (tipo in ('PAGO_POR_VENCER', 'PAGO_HOY', 'PAGO_VENCIDO',
        'SUGERENCIA_SUSPENSION', 'INTEGRACION_CON_ERROR')),
    constraint notificacion_uk unique nulls not distinct (tipo, negocio_id, modelo_ia_id, periodo)
);

create index notificacion_fecha_idx on notificacion_admin (fecha desc);

-- RF-11: resultado de la última prueba de conexión de cada modelo.
alter table modelo_ia add column ultima_prueba_exitosa boolean;
alter table modelo_ia add column ultima_prueba_mensaje varchar(300);
