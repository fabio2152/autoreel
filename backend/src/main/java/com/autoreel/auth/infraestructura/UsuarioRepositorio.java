package com.autoreel.auth.infraestructura;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.autoreel.auth.dominio.Rol;
import com.autoreel.auth.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

    @Query("select u from Usuario u left join fetch u.negocio where u.correo = :correo")
    Optional<Usuario> buscarPorCorreo(String correo);

    @Query("select u from Usuario u left join fetch u.negocio where u.id = :id")
    Optional<Usuario> buscarConNegocio(Long id);

    @Query("select u from Usuario u join fetch u.negocio where u.negocio.id = :negocioId")
    Optional<Usuario> buscarPorNegocio(Long negocioId);

    @Query("select u from Usuario u join fetch u.negocio where u.rol = com.autoreel.auth.dominio.Rol.NEGOCIO")
    List<Usuario> listarUsuariosDeNegocio();

    boolean existsByCorreo(String correo);

    boolean existsByRol(Rol rol);

    /**
     * RN-08: suma un intento fallido en una sola sentencia (sin carreras entre peticiones simultáneas).
     * Al llegar al máximo, bloquea la cuenta y reinicia el contador para el siguiente período.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update usuario
               set intentos_fallidos = case when intentos_fallidos + 1 >= :maximo then 0 else intentos_fallidos + 1 end,
                   bloqueado_hasta   = case when intentos_fallidos + 1 >= :maximo then :bloqueadoHasta else bloqueado_hasta end
             where id = :id
            """, nativeQuery = true)
    void registrarIntentoFallido(Long id, int maximo, Instant bloqueadoHasta);
}
