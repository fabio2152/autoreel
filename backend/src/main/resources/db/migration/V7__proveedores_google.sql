-- Dos caminos de Google para el texto y el video, elegibles desde la base de datos (RN-56):
--   * Google AI Studio (una clave de API): Gemini para el guion y Gemini Omni Flash para los clips.
--   * Google Cloud / Vertex AI (una cuenta de servicio): Gemini para el guion y Veo para los clips.
-- Por decisión del usuario (2026-11-05), quedan activos los de AI Studio; los de Google Cloud quedan listos e inactivos.

-- La cuenta de servicio de Google Cloud la comparten Veo y Gemini en Google Cloud.
update modelo_ia
set nombre_secreto = 'autoreel/google-cloud',
    estado         = 'INACTIVO'
where adaptador = 'VEO';

insert into modelo_ia (nombre, proveedor, tipo, adaptador, identificador_modelo, parametros,
                       costo_referencial, unidad_costo, nombre_secreto, estado)
values ('Gemini (Google Cloud)', 'GOOGLE', 'TEXTO', 'GEMINI_CLOUD', 'gemini-3.8-flash',
        '{"ubicacion": "global", "temperatura": 0.7}',
        0.010000, 'POR_SOLICITUD', 'autoreel/google-cloud', 'INACTIVO'),
       -- 720p: 5.792 tokens por segundo a 12 dólares por millón (unos 0,07 dólares por segundo); sin plan gratuito.
       ('Gemini Omni Flash', 'GOOGLE', 'VIDEO', 'GEMINI_OMNI', 'gemini-omni-1.2-flash',
        '{"relacionAspecto": "9:16", "resolucion": "720p", "duracionClipSegundos": 4, "tokensPorSegundo": 5792, "segundoPlano": false}',
        0.070000, 'POR_SEGUNDO', 'autoreel/gemini', 'ACTIVO');

-- El plan Lite usa AI Studio para el texto (ya lo hacía) y ahora también para el video. Los videos ya creados
-- conservan el modelo con el que nacieron (RN-54).
update plan
set modelo_video_id = (select id from modelo_ia
                       where adaptador = 'GEMINI_OMNI' and identificador_modelo = 'gemini-omni-1.2-flash')
where nombre = 'Lite';
