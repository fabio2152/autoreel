-- Comprobante de pago (2026-11-25): al registrar un pago, el administrador puede adjuntar la imagen del comprobante
-- (una captura de Yape u otra) de hasta 5 MB. Se guarda en negocios/{id}/comprobantes/ del almacenamiento; aquí va
-- solo su clave. Es opcional, así que los pagos anteriores siguen sin comprobante.

alter table pago add column comprobante_clave varchar(300);
