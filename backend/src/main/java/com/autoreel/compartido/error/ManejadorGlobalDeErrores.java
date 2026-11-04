package com.autoreel.compartido.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ManejadorGlobalDeErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalDeErrores.class);

    @ExceptionHandler(ExcepcionDeNegocio.class)
    ResponseEntity<ErrorApi> negocio(ExcepcionDeNegocio e) {
        return ResponseEntity.status(e.getEstado()).body(new ErrorApi(e.getCodigo(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorApi> validacion(MethodArgumentNotValidException e) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Revisa los datos ingresados.");
        return ResponseEntity.badRequest().body(new ErrorApi("DATOS_INVALIDOS", mensaje));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorApi> solicitudInvalida(Exception e) {
        return ResponseEntity.badRequest()
                .body(new ErrorApi("SOLICITUD_INVALIDA", "La solicitud no tiene un formato válido."));
    }

    /** Último resguardo ante dos operaciones simultáneas que chocan con una restricción única. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorApi> conflicto(DataIntegrityViolationException e) {
        log.warn("Conflicto de integridad: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorApi("CONFLICTO", "Los datos chocan con un registro existente. Revisa e inténtalo de nuevo."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorApi> noEncontrado(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorApi("NO_ENCONTRADO", "No encontramos lo que buscas."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorApi> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorApi("METODO_NO_PERMITIDO", "Esta operación no está permitida."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorApi> inesperado(Exception e) {
        log.error("Error inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorApi("ERROR_INTERNO", "Ocurrió un error inesperado. Inténtalo de nuevo en unos minutos."));
    }
}
