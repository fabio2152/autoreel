package com.autoreel.auth.infraestructura;

import java.util.List;
import java.util.Optional;

import com.autoreel.auth.dominio.Rol;
import com.autoreel.auth.dominio.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * Para el inicio de sesión: bloquea la fila hasta el fin de la transacción, de modo que los intentos
     * simultáneos con el mismo correo se verifican y se cuentan de a uno (RN-08).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.correo = :correo")
    Optional<Usuario> bloquearPorCorreo(String correo);
}
