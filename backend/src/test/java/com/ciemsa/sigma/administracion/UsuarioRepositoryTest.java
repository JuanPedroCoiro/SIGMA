package com.ciemsa.sigma.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.TestcontainersConfiguration;
import com.ciemsa.sigma.shared.seguridad.Rol;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class UsuarioRepositoryTest {

  private static final String HASH = "$2a$10$hashDePrueba";

  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private CuadrillaRepository cuadrillaRepository;
  @Autowired private EntityManager entityManager;

  private Cuadrilla cuadrilla;

  @BeforeEach
  void setUp() {
    cuadrilla = cuadrillaRepository.save(Cuadrilla.crear("Cuadrilla Norte", "Norte"));
  }

  @Test
  void save_usuarioNuevo_completaIdFechasYVersion() {
    Usuario usuario =
        usuarioRepository.saveAndFlush(
            Usuario.crear("Juan", "juan@ciemsa.com", HASH, Rol.TECNICO, cuadrilla));

    assertThat(usuario.getId()).isNotNull();
    assertThat(usuario.getCreadoEn()).isNotNull();
    assertThat(usuario.getModificadoEn()).isNotNull();
    assertThat(usuario.getVersion()).isZero();
  }

  @Test
  void save_correoRepetido_violaLaRestriccionUnica() {
    usuarioRepository.saveAndFlush(operador("ana@ciemsa.com"));

    assertThatThrownBy(() -> usuarioRepository.saveAndFlush(operador("ana@ciemsa.com")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findByCorreo_correoExistente_loEncuentra() {
    usuarioRepository.save(operador("ana@ciemsa.com"));

    assertThat(usuarioRepository.findByCorreo("ana@ciemsa.com")).isPresent();
    assertThat(usuarioRepository.findByCorreo("otro@ciemsa.com")).isEmpty();
  }

  @Test
  void existsByCorreoAndIdNot_distingueLaPropiaCuentaDeOtra() {
    Usuario ana = usuarioRepository.save(operador("ana@ciemsa.com"));
    Usuario beto = usuarioRepository.save(operador("beto@ciemsa.com"));

    assertThat(usuarioRepository.existsByCorreoAndIdNot("ana@ciemsa.com", ana.getId())).isFalse();
    assertThat(usuarioRepository.existsByCorreoAndIdNot("ana@ciemsa.com", beto.getId())).isTrue();
  }

  @Test
  void countByRolAndActivoTrue_noCuentaLosInactivos() {
    usuarioRepository.save(jefatura("jefa1@ciemsa.com"));
    Usuario inactiva = jefatura("jefa2@ciemsa.com");
    inactiva.darDeBaja();
    usuarioRepository.save(inactiva);

    assertThat(usuarioRepository.countByRolAndActivoTrue(Rol.JEFATURA)).isEqualTo(1);
  }

  @Test
  void findAll_paginado_traeLaCuadrillaCargada() {
    usuarioRepository.save(Usuario.crear("Juan", "juan@ciemsa.com", HASH, Rol.TECNICO, cuadrilla));
    entityManager.flush();
    entityManager.clear();

    Page<Usuario> pagina = usuarioRepository.findAll(PageRequest.of(0, 20));

    assertThat(pagina.getContent()).hasSize(1);
    assertThat(Hibernate.isInitialized(pagina.getContent().getFirst().getCuadrilla())).isTrue();
  }

  private static Usuario operador(String correo) {
    return Usuario.crear("Ana", correo, HASH, Rol.OPERADOR, null);
  }

  private static Usuario jefatura(String correo) {
    return Usuario.crear("Jefa", correo, HASH, Rol.JEFATURA, null);
  }
}
