-- El negocio que olvidó su contraseña le avisa al administrador desde Ajustes. Una por negocio y por día
-- (notificacion_uk con el día de Lima como período).
alter table notificacion_admin drop constraint notificacion_tipo_ck;
alter table notificacion_admin add constraint notificacion_tipo_ck check (tipo in ('PAGO_POR_VENCER', 'PAGO_HOY',
    'PAGO_VENCIDO', 'SUGERENCIA_SUSPENSION', 'INTEGRACION_CON_ERROR', 'SOLICITUD_CONTRASENA'));
