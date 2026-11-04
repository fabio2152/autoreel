package com.autoreel.compartido.error;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce los errores a {@link ErrorApi}. Los errores estándar de Spring MVC (ruta inexistente, método o formato
 * no admitido, cuerpo ilegible, parámetro faltante...) conservan el estado HTTP que calcula Spring.
 */
@RestControllerAdvice
public class ManejadorGlobalDeErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalDeErrores.class);

    @ExceptionHandler(ExcepcionDeNegocio.class)
    ResponseEntity<ErrorApi> negocio(ExcepcionDeNegocio e) {
        return ResponseEntity.status(e.getEstado()).body(new ErrorApi(e.getCodigo(), e.getMessage()));
    }

    /** Último resguardo ante dos operaciones simultáneas que chocan con una restricción única. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorApi> conflicto(DataIntegrityViolationException e) {
        // Solo el nombre de la restricción: el detalle de PostgreSQL incluye datos personales (por ejemplo, el correo).
        String restriccion = e.getCause() instanceof ConstraintViolationException violacion
                ? violacion.getConstraintName() : e.getClass().getSimpleName();
        log.warn("Conflicto de integridad: {}", restriccion);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorApi("CONFLICTO", "Los datos chocan con un registro existente. Revisa e inténtalo de nuevo."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorApi> inesperado(Exception e) {
        log.error("Error inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorApi("ERROR_INTERNO", "Ocurrió un error inesperado. Inténtalo de nuevo en unos minutos."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Revisa los datos ingresados.");
        return ResponseEntity.badRequest().headers(cabeceras).body(new ErrorApi("DATOS_INVALIDOS", mensaje));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object cuerpo, HttpHeaders cabeceras,
            HttpStatusCode estado, WebRequest peticion) {
        ErrorApi error = switch (estado.value()) {
            case 404 -> new ErrorApi("NO_ENCONTRADO", "No encontramos lo que buscas.");
            case 405 -> new ErrorApi("METODO_NO_PERMITIDO", "Esta operación no está permitida.");
            case 415, 406 -> new ErrorApi("FORMATO_NO_ADMITIDO", "El formato de la solicitud no es compatible.");
            default -> estado.is4xxClientError()
                    ? new ErrorApi("SOLICITUD_INVALIDA", "La solicitud no tiene un formato válido.")
                    : new ErrorApi("ERROR_INTERNO", "Ocurrió un error inesperado. Inténtalo de nuevo en unos minutos.");
        };
        if (estado.is5xxServerError()) {
            log.error("Error del servidor", e);
        }
        return ResponseEntity.status(estado).headers(cabeceras).body(error);
    }
}
