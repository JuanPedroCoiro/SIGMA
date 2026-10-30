# Guía de arquitectura y convenciones — SIGMA

> Copia en Markdown de la guía de trabajo (versión del 18/10/2026). Si cambia una regla, se actualiza este archivo por PR.

## Propósito y principios

SIGMA es un monolito modular en capas: siete módulos de dominio, los de la Fig. 19 del anteproyecto, y dentro de cada uno cuatro capas que se comunican con reglas fijas, verificadas con tests. Esta guía es la referencia para que los dos escribamos el código igual desde el sprint 1. Detalla para el código los estándares que el anteproyecto fija en 1.13.1.6.1.

Principios:

1. **El anteproyecto manda.** Los nombres del dominio salen de la Fig. 7, los estados de la Fig. 18, los módulos de la Fig. 19 y las convenciones de 1.13.1.6.1. Si la guía choca con el anteproyecto, se corrige la guía. Si hace falta cambiar algo del anteproyecto, se registra como cambio en el capítulo 2, con su justificación.
2. **Consistencia antes que perfección.** Una forma de hacer cada cosa, aplicada en todos los módulos. Si hay dudas, se copia la estructura del ejemplo «Registrar incidente» de esta guía.
3. **Abstracción solo donde hay variación real.** Interfaces y patrones cuando resuelven un problema concreto (dos implementaciones, reglas que cambian, estados), no por costumbre.
4. **El dominio no depende de la infraestructura.** Las reglas de negocio (estados de la OT, vencimientos, stock) no conocen HTTP, JSON ni detalles de la base.
5. **Las reglas se verifican solas.** Lo que se puede chequear con una herramienta (capas, formato, migraciones) se chequea en la integración continua, no en la revisión manual.
6. **Escalable = fácil de cambiar.** Que sumar un organismo, un módulo o cambiar el almacenamiento de fotos no obligue a tocar el resto. No implica microservicios.

Capacidad de referencia: 2 integrantes × 17 h/semana (498 horas-persona en total), MVP en diciembre. Toda regla de esta guía tiene que pagar su costo en horas.

## Vista general

Un solo backend desplegable con los siete módulos de dominio de la Fig. 19 del anteproyecto; cada módulo tiene las mismas cuatro capas. El frontend es una SPA que habla con el backend por REST, con la sesión en una cookie, y toma las teselas del mapa de OpenStreetMap.

Las cuatro capas completas son como queda `ot`. Los módulos livianos usan solo una parte (ver «Backend: paquetes»).

Las flechas de la izquierda marcan quién depende de quién: `api` usa `application`, `application` usa `domain` (y los repositorios de `infrastructure` de su propio módulo), e `infrastructure` implementa lo que `domain` define. Entre módulos, la comunicación es por servicio o por evento, como `IncidenteRegistrado` hacia auditoría. Seguridad, archivos, tareas programadas (que marcan las OTs vencidas) y manejo de errores son servicios transversales en `shared/`.

## Backend: paquetes y reglas de dependencia

El código se organiza primero por módulo y después por capa. Los módulos son los siete de la Fig. 19 del anteproyecto, que a su vez siguen los módulos de requerimientos de 1.8. Todos tienen las mismas cuatro capas, aunque alguna quede casi vacía.

```text
com.ciemsa.sigma
├── SigmaApplication.java
├── shared/                 # transversal, sin lógica de negocio
│   ├── api/                # GlobalExceptionHandler, PageResponse
│   ├── domain/             # BaseEntity, DomainException, eventos base
│   ├── seguridad/          # Spring Security: sesión, hash, roles, UsuarioActual
│   ├── archivos/           # AlmacenArchivos y sus adaptadores
│   └── tareas/             # tareas programadas (marcar OTs vencidas)
├── ot/                     # Módulo 2 (núcleo): estructura COMPLETA
│   ├── api/                # IncidenteController, OrdenTrabajoController, DTOs
│   ├── application/        # IncidenteService, OrdenTrabajoService
│   ├── domain/             # Incidente, OrdenTrabajo, EstadoOT, ReglaSLA, eventos
│   └── infrastructure/     # repositorios Spring Data y adaptadores
├── inventario/             # Módulo 4: estructura LIVIANA (ejemplo)
│   ├── MaterialController.java
│   ├── MaterialService.java
│   ├── Material.java       # entidad JPA
│   ├── MaterialRepository.java
│   └── dto/                # records de request/response
├── activos/                # Módulo 1: instalaciones, equipos, ficha, mapa (liviano)
├── planificacion/          # Módulo 3: servicio + controller (sin entidades)
├── pendientes/             # Módulo 5: consultas de lectura con joins + controller
├── auditoria/              # Módulo 6: bitácora (liviano + listener de eventos)
└── administracion/         # Módulo 7: usuarios, cuadrillas (liviano) + importación
```

Cada paquete se crea cuando una historia lo necesita, no todos de entrada.

| Módulo (Fig. 19) | Paquete | Entidades propias (Fig. 7) | RF |
| --- | --- | --- | --- |
| 1 Activos | `activos` | Instalacion, Equipo | RF-1 a RF-3 |
| 2 Incidentes y OTs | `ot` | Incidente, OrdenTrabajo, Reprogramacion, Adjunto, Organismo, CategoriaFalla, ReglaSLA | RF-4 a RF-7, RF-22, RF-30 |
| 3 Planificación | `planificacion` | Ninguna: opera sobre las OTs por el servicio de `ot` | RF-8 a RF-10 |
| 4 Inventario | `inventario` | Material, MovimientoStock | RF-11 a RF-15 |
| 5 Pendientes y reportes | `pendientes` | Ninguna: consultas sobre `ot` | RF-16 a RF-19 |
| 6 Auditoría | `auditoria` | RegistroAuditoria | RF-21 |
| 7 Administración | `administracion` | Usuario, Cuadrilla | RF-23 a RF-29 (RF-20 está en shared/seguridad) |

Las pantallas de alta y edición de instalaciones y equipos (RF-24 a RF-26) están en `administracion`, como en la Fig. 19, pero las entidades son de `activos` y se modifican por su servicio. Los catálogos que usa el cálculo del vencimiento (Organismo, CategoriaFalla, ReglaSLA) quedan en `ot` porque en el MVP se cargan por script y solo los usa ese módulo.

| Capa | Contiene | Puede usar | No puede usar |
| --- | --- | --- | --- |
| `api` | Controllers REST, DTOs de request/response, mappers | `application` del mismo módulo | `infrastructure`, entidades fuera del mapper |
| `application` | Servicios de caso de uso, `@Transactional`, permisos por rol | `domain`, repositorios, servicios `application` de otros módulos | Clases de `api` (DTOs HTTP) |
| `domain` | Entidades, value objects, enums, reglas, eventos, interfaces de puertos | Solo `domain` y `shared.domain` | Spring Web, repositorios, otros módulos salvo por id |
| `infrastructure` | Repositorios Spring Data, almacenamiento de archivos, clientes externos | `domain` | `api`, `application` |

Reglas entre módulos:

- Un módulo usa a otro **solo a través de su servicio `application`**, nunca de su repositorio ni de sus entidades.
- Las relaciones entre entidades de distintos módulos se guardan **por id** (`idInstalacion`, `idCuadrilla`), no con `@ManyToOne` a la entidad del otro módulo. En la base sí hay clave foránea. Esto vale para escribir; para leer, las pantallas que cruzan módulos (pendientes, ficha con historial, mapa) usan consultas con joins que devuelven un DTO.
- Los efectos secundarios entre módulos (bitácora, descuento de stock al cerrar) van por **eventos de dominio**, dentro de la misma transacción, como pide el anteproyecto para el registro de incidentes y el cierre (Anexo A.2).

**Estructura según el módulo.** La tabla de capas aplica completa solo donde hay reglas de negocio que proteger. La regla: **estructura completa donde hay reglas, mínima donde hay altas y consultas.**

| Tipo de módulo | Cuáles | Estructura |
| --- | --- | --- |
| Con reglas | `ot` (State de la OT, cálculo del SLA) | Las 4 capas, con los repositorios en `infrastructure` |
| Liviano | `activos`, `inventario`, `auditoria`, `administracion` (salvo la importación) | Controller + servicio + entidad + repositorio de Spring Data + DTO como `record` |
| Solo consultas | `pendientes`, `planificacion` | Servicio + controller, con consultas de lectura que devuelven DTO |

Un solo modelo: las entidades llevan anotaciones JPA. Separar modelo de dominio y de persistencia duplicaría clases sin beneficio a esta escala. En los módulos livianos el repositorio va junto a la entidad, sin paquete infrastructure; fuera de ot, solo los adaptadores reales (`AlmacenArchivos`, `LectorPlanilla`) son los únicos que justifican `infrastructure`.

## Cuándo usar interfaces

Se crea una interfaz cuando hay, o puede haber pronto, más de una implementación, o cuando el dominio necesita algo de la infraestructura sin depender de ella. Para un servicio con una sola implementación, no.

| Caso | ¿Interfaz? | Motivo |
| --- | --- | --- |
| Almacenamiento de fotos (`AlmacenArchivos`) | Sí | Disco local en desarrollo, almacenamiento de objetos en la nube (1.11.1). Es el «adaptador de archivos» de la Fig. 19 |
| Usuario actual (`UsuarioActual`) | Sí | Aísla Spring Security: los servicios piden el usuario y su rol sin depender del framework |
| Lector de planillas (`LectorPlanilla`) | Sí | La importación de instalaciones (RF-29) acepta XLSX y CSV con la misma salida |
| Fecha y hora actual | Ya existe | Se inyecta `java.time.Clock` para probar vencimientos con fechas fijas |
| Cálculo del vencimiento | No | Lo resuelve `ReglaSLA` con datos de la base (RNF-9): un organismo nuevo es una fila, no código |
| Notificaciones | No | El anteproyecto no tiene notificaciones: la OT aparece en «Mis OTs» y las alertas se ven en pantalla |
| Repositorios | Ya lo son | Spring Data genera la implementación |
| `IncidenteService`, `OrdenTrabajoService`, etc. | No | Una sola implementación; se prueban con Mockito igual |
| Mappers | No | MapStruct genera la clase |

`AlmacenArchivos` vive en `shared/archivos` y `LectorPlanilla` en `administracion`; sus implementaciones van en `infrastructure` y se eligen por perfil de Spring (`@Profile("dev")`, `@Profile("prod")`).

## Patrones de diseño aplicados

Se usan nueve patrones, cada uno en un lugar concreto del dominio y la mayoría vistos en Diseño de Aplicaciones (la comparación está en el capítulo 2). No hace falta usarlos todos: se aplican cuando el código los pide. Un patrón nuevo se anota en la tabla de decisiones de «Qué evitar».

| Patrón | Dónde | Qué resuelve |
| --- | --- | --- |
| **Fachada (una por módulo)** | Servicio `application` de cada módulo | Punto de entrada único a cada módulo: la API y los demás módulos solo hablan con él |
| **Experto (GRASP)** | Entidades de `domain` | Cada regla vive en la clase que tiene los datos: `OrdenTrabajo` decide qué se puede hacer con ella, `ReglaSLA` calcula el vencimiento |
| **State** | `EstadoOT`: cada estado define qué operaciones admite | Las transiciones de la Fig. 18 quedan en un único punto, como pide el Anexo A.5 |
| **Reglas como datos** | `ReglaSLA.calcularVencimiento` | Un organismo o un plazo nuevo es una fila en la base, no código (RNF-9) |
| **Repository** | `*Repository` en `infrastructure` | Acceso a datos con Spring Data; consultas complejas con `@Query` o Specifications |
| **Observer (eventos de dominio)** | `IncidenteRegistrado`, `OrdenTrabajoCerrada`... | Bitácora, descuento de stock y alertas sin acoplar módulos, en la misma transacción |
| **Operación idempotente** | Cierre técnico | Cada envío lleva un identificador de operación; un reintento no duplica el cierre ni el descuento (R8) |
| **Adapter** | `AlmacenArchivos` | El almacenamiento de fotos es reemplazable (Fig. 19) |
| **DTO + Mapper** | `api` de cada módulo, MapStruct | Las entidades nunca salen por HTTP; el contrato de la API es estable |

**State.** Es el mismo patrón que en el obligatorio de DA (una clase por estado), escrito como enum: cada constante de `EstadoOT` es un estado y sobreescribe solo las operaciones que admite; el resto lanza `TransicionInvalidaException`. Las operaciones son las de la Fig. 7 (`asignar`, `iniciar`, `cerrarTecnicamente`, `validar`, `observar`, `reprogramar`, `anular`) más `suspender`, para el flujo 3a de CU-3. Al ser un enum, se guarda en la base como texto (`@Enumerated(EnumType.STRING)`) sin conversiones.

```java
// ot/domain/EstadoOT.java: cada estado sabe qué operaciones admite (Fig. 18)
public enum EstadoOT {
    PENDIENTE {
        @Override void asignar(OrdenTrabajo ot, Long idCuadrilla, LocalDate fecha) {   // asignar o tomar
            ot.registrarAsignacion(idCuadrilla, fecha);
            ot.cambiarEstado(ASIGNADA);
        }
        @Override void anular(OrdenTrabajo ot, String motivo) {
            ot.registrarAnulacion(motivo);
            ot.cambiarEstado(ANULADA);
        }
    },
    ASIGNADA {
        @Override void asignar(OrdenTrabajo ot, Long idCuadrilla, LocalDate fecha) {   // reasignar
            ot.registrarAsignacion(idCuadrilla, fecha);
        }
        @Override void reprogramar(OrdenTrabajo ot, LocalDate fecha, String motivo) {
            ot.registrarReprogramacion(fecha, motivo);
        }
        @Override void iniciar(OrdenTrabajo ot) {
            ot.cambiarEstado(EN_PROCESO);
        }
        @Override void anular(OrdenTrabajo ot, String motivo) {
            ot.registrarAnulacion(motivo);
            ot.cambiarEstado(ANULADA);
        }
    },
    EN_PROCESO {
        @Override void suspender(OrdenTrabajo ot, String motivo) {                     // CU-3, 3a
            ot.registrarSuspension(motivo);
            ot.cambiarEstado(ASIGNADA);
        }
        @Override void cerrarTecnicamente(OrdenTrabajo ot, CierreTecnico cierre) {
            ot.registrarCierre(cierre);              // diagnóstico, solución y al menos una foto (RN-06)
            ot.cambiarEstado(CERRADA_TECNICA);
        }
    },
    CERRADA_TECNICA {
        @Override void validar(OrdenTrabajo ot, Long idUsuario) {
            ot.registrarValidacion(idUsuario);
            ot.cambiarEstado(VALIDADA);
        }
        @Override void observar(OrdenTrabajo ot, String motivo) {   // Jefatura, o el Operador si no se restableció
            ot.registrarObservacion(motivo);
            ot.cambiarEstado(ASIGNADA);
        }
    },
    VALIDADA,
    ANULADA;

    // Por defecto ninguna operación está permitida: cada estado habilita solo las suyas
    void asignar(OrdenTrabajo ot, Long idCuadrilla, LocalDate fecha) { throw invalida("asignar"); }
    void reprogramar(OrdenTrabajo ot, LocalDate fecha, String motivo) { throw invalida("reprogramar"); }
    void iniciar(OrdenTrabajo ot) { throw invalida("iniciar"); }
    void suspender(OrdenTrabajo ot, String motivo) { throw invalida("suspender"); }
    void cerrarTecnicamente(OrdenTrabajo ot, CierreTecnico cierre) { throw invalida("cerrar"); }
    void validar(OrdenTrabajo ot, Long idUsuario) { throw invalida("validar"); }
    void observar(OrdenTrabajo ot, String motivo) { throw invalida("observar"); }
    void anular(OrdenTrabajo ot, String motivo) { throw invalida("anular"); }

    TransicionInvalidaException invalida(String operacion) {
        return new TransicionInvalidaException(this, operacion);
    }
}
```

`OrdenTrabajo` delega en su estado, y solo `EstadoOT` puede cambiarlo (los métodos `registrar...` y `cambiarEstado` son de paquete). Los servicios nunca hacen `ot.setEstado(...)`.

```java
// ot/domain/OrdenTrabajo.java (extracto)
public void asignar(Long idCuadrilla, LocalDate fecha) { estado.asignar(this, idCuadrilla, fecha); }
public void iniciar() { estado.iniciar(this); }
public void suspender(String motivo) { estado.suspender(this, motivo); }
public void cerrarTecnicamente(CierreTecnico cierre) { estado.cerrarTecnicamente(this, cierre); }
// ... validar, observar, reprogramar y anular, igual

void cambiarEstado(EstadoOT nuevo) { this.estado = nuevo; }
```

Una OT nace `PENDIENTE` (desde un incidente o como preventiva) o `EN_PROCESO` (intervención no planificada, CU-21). El vencimiento no es un estado: lo calcula `estaVencida()`. El incidente tiene su propio estado; el anteproyecto define «En espera de tercero», y los otros dos nombres del ejemplo son propuestos.

```java
@Entity
@Table(name = "regla_sla")
public class ReglaSLA extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_regla_sla") private Long id;
    @Column(name = "id_organismo") private Long idOrganismo;
    @Enumerated(EnumType.STRING) private Prioridad prioridad;
    private int horasRespuesta;
    private int horasResolucion;

    // Fecha límite de la OT. Confirmar con CIEMSA si el contrato la mide
    // con las horas de resolución (supuesto actual) o con las de respuesta.
    public LocalDateTime calcularVencimiento(LocalDateTime desde) {
        return desde.plusHours(horasResolucion);
    }
}
```

La comparación con el obligatorio de Diseño de Aplicaciones (qué patrones se reutilizan y cuáles no) está en el capítulo 2 del informe, sección 2.2.3.

## Ejemplo completo: Registrar incidente

«Registrar incidente» (CU-1, HU-03, sprint 2) es la plantilla para el resto de las funcionalidades: recorre las cuatro capas, consulta otro módulo por su servicio, aplica reglas del dominio y publica un evento. Es también como nace una OT correctiva según el anteproyecto. Los nombres siguen la Fig. 7 y la Fig. 20; se ajustan cuando se cierre el modelo en el sprint 1.

`BaseEntity` es un `@MappedSuperclass` con `creadoEn`, `modificadoEn` y `version`. Cada entidad declara su propia clave con el nombre `id_<entidad>` (1.13.1.6.1).

**1. API: request, response y controller**

```java
// ot/api/RegistrarIncidenteRequest.java
public record RegistrarIncidenteRequest(
    @NotNull Long idInstalacion,
    Long idEquipo,                                // opcional
    @NotNull OrigenIncidente origen,              // RECLAMO_ORGANISMO o MONITOREO
    @NotNull Long idCategoriaFalla,
    @NotNull Prioridad prioridad,                 // la propone la categoría y el actor la confirma (RN-01)
    @NotBlank @Size(max = 2000) String descripcion,
    @Valid FallaDeTercero fallaDeTercero) {}      // solo en el flujo 4b

public record FallaDeTercero(@NotBlank String tercero, @NotBlank String causa) {}

// ot/api/IncidenteResponse.java
public record IncidenteResponse(
    String numero,                  // INC-nnnn
    EstadoIncidente estado,
    String numeroOrdenTrabajo,      // OT-nnnn; null si queda en espera de tercero
    LocalDateTime fechaLimiteSla,   // null = «SLA sin definir»
    LocalDateTime fechaReporte) {}

// ot/api/IncidenteController.java
@RestController
@RequestMapping("/api/v1/incidentes")
@RequiredArgsConstructor
class IncidenteController {
    private final IncidenteService incidenteService;
    private final IncidenteMapper mapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERADOR','JEFATURA')")
    ResponseEntity<IncidenteResponse> registrar(@Valid @RequestBody RegistrarIncidenteRequest req) {
        Incidente incidente = incidenteService.registrar(mapper.toCommand(req));
        return ResponseEntity
            .created(URI.create("/api/v1/incidentes/" + incidente.getId()))
            .body(mapper.toResponse(incidente));
    }
}
```

**2. Application: el caso de uso**

```java
// ot/application/IncidenteService.java
@Service
@RequiredArgsConstructor
@Transactional   // incidente, OT y bitácora en una única transacción (CU-1, paso 9)
public class IncidenteService {
    private final IncidenteRepository incidenteRepository;
    private final OrdenTrabajoRepository ordenTrabajoRepository;
    private final ReglaSlaRepository reglaSlaRepository;
    private final InstalacionService instalacionService;   // módulo activos, por su servicio
    private final UsuarioActual usuarioActual;
    private final Clock clock;
    private final ApplicationEventPublisher eventos;

    public Incidente registrar(RegistrarIncidenteCommand cmd) {
        // 3a: lanza NoEncontradoException si no existe o está inactiva
        InstalacionResumen instalacion = instalacionService.obtenerActiva(cmd.idInstalacion());
        LocalDateTime ahora = LocalDateTime.now(clock);
        // paso 7 (RF-5, Etapa 2): verificar duplicados

        Incidente incidente = Incidente.registrar(cmd.idInstalacion(), cmd.idEquipo(), cmd.origen(),
            cmd.idCategoriaFalla(), cmd.prioridad(), cmd.descripcion(), usuarioActual.id(), ahora);
        if (cmd.fallaDeTercero() != null) {                                   // 4b: sin OT ni SLA
            incidente.ponerEnEsperaDeTercero(cmd.fallaDeTercero().tercero(), cmd.fallaDeTercero().causa());
        }
        incidenteRepository.save(incidente);

        if (!incidente.estaEnEsperaDeTercero()) {
            LocalDateTime fechaLimite = reglaSlaRepository
                .findByIdOrganismoAndPrioridad(instalacion.idOrganismo(), cmd.prioridad())
                .map(regla -> regla.calcularVencimiento(ahora))
                .orElse(null);                                                  // 8a: «SLA sin definir»
            ordenTrabajoRepository.save(OrdenTrabajo.correctiva(incidente, fechaLimite));
        }

        eventos.publishEvent(new IncidenteRegistrado(incidente.getId(), usuarioActual.id(), ahora));
        return incidente;
    }
}
```

`InstalacionResumen` es un record que devuelve el servicio de `activos`: los módulos se pasan records, no entidades.

**3. Domain: la entidad protege sus reglas**

```java
// ot/domain/OrdenTrabajo.java
@Entity
@Table(name = "orden_trabajo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrdenTrabajo extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden_trabajo") private Long id;
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false) private String numero;   // OT-nnnn, lo asigna la base
    @Enumerated(EnumType.STRING) private TipoOT tipo;                       // CORRECTIVA, PREVENTIVA
    @Enumerated(EnumType.STRING) private EstadoOT estado;
    @OneToOne @JoinColumn(name = "id_incidente") private Incidente incidente;  // mismo módulo
    @Column(name = "id_instalacion") private Long idInstalacion;            // otro módulo: por id
    @Column(name = "id_cuadrilla") private Long idCuadrilla;
    private LocalDate fechaProgramada;
    private LocalDateTime fechaLimiteSla;                                   // null = «SLA sin definir»

    public static OrdenTrabajo correctiva(Incidente incidente, LocalDateTime fechaLimiteSla) {
        OrdenTrabajo ot = new OrdenTrabajo();
        ot.tipo = TipoOT.CORRECTIVA;
        ot.estado = EstadoOT.PENDIENTE;
        ot.incidente = incidente;
        ot.idInstalacion = incidente.getIdInstalacion();
        ot.fechaLimiteSla = fechaLimiteSla;
        return ot;
    }

    // Operaciones de la Fig. 7: delegan en el estado (patrón State)
    public void asignar(Long idCuadrilla, LocalDate fecha) { estado.asignar(this, idCuadrilla, fecha); }

    // Solo las usa EstadoOT
    void registrarAsignacion(Long idCuadrilla, LocalDate fecha) {
        this.idCuadrilla = idCuadrilla;
        this.fechaProgramada = fecha;
    }

    void cambiarEstado(EstadoOT nuevo) { this.estado = nuevo; }
}
```

El estado del incidente es otro enum, `EstadoIncidente`: `EN_ESPERA_TERCERO` viene del anteproyecto; `CON_OT` y `CERRADO_SIN_OT` son nombres propuestos.

**4. Infrastructure: repositorios y migración**

```java
// ot/infrastructure/OrdenTrabajoRepository.java
public interface OrdenTrabajoRepository extends JpaRepository<OrdenTrabajo, Long>,
                                                JpaSpecificationExecutor<OrdenTrabajo> {
    List<OrdenTrabajo> findByIdCuadrillaAndEstadoIn(Long idCuadrilla, Collection<EstadoOT> estados);
}

// ot/infrastructure/ReglaSlaRepository.java
public interface ReglaSlaRepository extends JpaRepository<ReglaSLA, Long> {
    Optional<ReglaSLA> findByIdOrganismoAndPrioridad(Long idOrganismo, Prioridad prioridad);
}
```

```sql
-- src/main/resources/db/migration/V4__crear_incidente_y_orden_trabajo.sql
CREATE SEQUENCE seq_numero_incidente;
CREATE SEQUENCE seq_numero_orden_trabajo;

CREATE TABLE incidente (
    id_incidente        BIGSERIAL PRIMARY KEY,
    numero              VARCHAR(12)   NOT NULL UNIQUE
                        DEFAULT ('INC-' || LPAD(nextval('seq_numero_incidente')::text, 4, '0')),
    id_instalacion      BIGINT        NOT NULL REFERENCES instalacion(id_instalacion),
    id_equipo           BIGINT        REFERENCES equipo(id_equipo),
    id_categoria_falla  BIGINT        NOT NULL REFERENCES categoria_falla(id_categoria_falla),
    origen              VARCHAR(20)   NOT NULL,
    prioridad           VARCHAR(20)   NOT NULL,
    estado              VARCHAR(20)   NOT NULL,
    descripcion         VARCHAR(2000) NOT NULL,
    tercero             VARCHAR(100),
    causa               VARCHAR(500),
    fecha_reporte       TIMESTAMP     NOT NULL,
    id_usuario          BIGINT        NOT NULL REFERENCES usuario(id_usuario),
    creado_en           TIMESTAMP     NOT NULL,
    modificado_en       TIMESTAMP     NOT NULL,
    version             INTEGER       NOT NULL DEFAULT 0
);

CREATE TABLE orden_trabajo (
    id_orden_trabajo    BIGSERIAL PRIMARY KEY,
    numero              VARCHAR(12)   NOT NULL UNIQUE
                        DEFAULT ('OT-' || LPAD(nextval('seq_numero_orden_trabajo')::text, 4, '0')),
    tipo                VARCHAR(20)   NOT NULL,
    estado              VARCHAR(20)   NOT NULL,
    id_incidente        BIGINT        REFERENCES incidente(id_incidente),
    id_instalacion      BIGINT        NOT NULL REFERENCES instalacion(id_instalacion),
    id_equipo           BIGINT        REFERENCES equipo(id_equipo),
    id_cuadrilla        BIGINT        REFERENCES cuadrilla(id_cuadrilla),
    fecha_programada    DATE,
    fecha_limite_sla    TIMESTAMP,                    -- NULL = SLA sin definir
    diagnostico         VARCHAR(2000),
    solucion            VARCHAR(2000),
    creado_en           TIMESTAMP     NOT NULL,
    modificado_en       TIMESTAMP     NOT NULL,
    version             INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX ix_orden_trabajo_estado    ON orden_trabajo(estado);
CREATE INDEX ix_orden_trabajo_cuadrilla ON orden_trabajo(id_cuadrilla);
```

**5. Efecto secundario por evento (otro módulo)**

```java
// auditoria/application/BitacoraListener.java
@Component
@RequiredArgsConstructor
class BitacoraListener {
    private final BitacoraService bitacora;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)   // misma transacción
    void on(IncidenteRegistrado e) {
        bitacora.registrar("INCIDENTE", e.idIncidente(), "REGISTRADO", null, e.resumen(), e.idUsuario(), e.ocurridoEn());
    }
}
```

**6. Tests mínimos de la funcionalidad**

- [ ] Unitario de dominio: `EstadoOT` rechaza las transiciones que no están en la Fig. 18 (sin Spring).
- [ ] Unitario de `ReglaSLA`: vencimiento por prioridad (RN-02).
- [ ] Unitario de `IncidenteService` con Mockito: falla de tercero sin OT (4b), OT sin fecha límite cuando no hay regla (8a), instalación inactiva (3a) y publicación de `IncidenteRegistrado`.
- [ ] Integración con `@SpringBootTest` y Testcontainers (PostgreSQL): 201 con datos válidos, 400 con datos inválidos, 403 con rol Técnico, e incidente, OT y bitácora en la misma transacción.

## Convenciones

**Idioma:** el dominio va en español, con los nombres de la Fig. 7 (`OrdenTrabajo`, `ReglaSLA`, `asignar`, `cerrarTecnicamente`); los términos técnicos quedan en inglés (`Controller`, `Service`, `Repository`, `Request`, `Response`). Sin tildes ni ñ en el código.

**Estilo:** Google Java Style Guide para Java, aplicado con Spotless y google-java-format, y Airbnb JavaScript Style Guide para React, con ESLint y Prettier, como fija 1.13.1.6.1.

**Nombres**

| Elemento | Convención | Ejemplo |
| --- | --- | --- |
| Controller | `<Entidad>Controller` | `IncidenteController`, `OrdenTrabajoController` |
| Servicio de caso de uso | `<Entidad>Service` | `IncidenteService` |
| Repositorio | `<Entidad>Repository` | `OrdenTrabajoRepository` |
| DTO de entrada | `<Accion><Entidad>Request` | `RegistrarIncidenteRequest` |
| DTO de salida | `<Entidad>Response` | `OrdenTrabajoResponse`, `OrdenTrabajoResumenResponse` |
| Comando de aplicación | `<Accion><Entidad>Command` | `CerrarOrdenTrabajoCommand` |
| Evento | Entidad + participio pasado | `IncidenteRegistrado`, `OrdenTrabajoCerrada` |
| Excepción de negocio | `<Problema>Exception` | `TransicionInvalidaException` |
| Tabla y columna | snake\_case en singular | `orden_trabajo.fecha_limite_sla` |
| Clave primaria | `id_<entidad>` | `id_orden_trabajo` |
| Clave foránea | `id_<entidad referida>` | `id_instalacion` |
| Migración Flyway | `V<n>__<verbo>_<objeto>.sql` | `V4__crear_incidente_y_orden_trabajo.sql` |
| Número visible para el usuario | `INC-nnnn`, `OT-nnnn` | `OT-0315` |

**API REST**

- Prefijo `/api/v1` y recursos en plural, como fija 1.13.1.6.1: `/incidentes`, `/ordenes-trabajo`, `/instalaciones`, `/materiales`, `/movimientos-stock`.
- Las acciones de negocio que cambian el estado son subrecursos con POST: `POST /ordenes-trabajo/{id}/asignacion`, `.../cierre-tecnico`, `.../validacion`.
- Códigos: 200 lectura, 201 creación con `Location`, 204 sin cuerpo, 400 validación, 401 sin sesión, 403 rol, 404 no existe, 409 conflicto de estado o de versión.
- **Listados paginados desde la primera pantalla** (decidido el 17/10): `?page=0&size=20&sort=fechaLimiteSla,asc`; respuesta `PageResponse<T>` con `content`, `page`, `size`, `totalElements`. Tamaño por defecto 20 y máximo 100 (`spring.data.web.pageable.max-page-size=100`). Los filtros van como parámetros (`?estado=ASIGNADA&idCuadrilla=3`).
  - **Se paginan:** OT, incidentes, instalaciones, movimientos de stock, auditoría y usuarios, es decir, todo lo que crece con el uso.
  - **No se paginan:** los catálogos chicos (organismos, categorías de falla, reglas de SLA, cuadrillas), que se devuelven como lista completa para los desplegables.
  - **El mapa no se pagina:** devuelve todas las instalaciones con solo id, coordenadas y estado, y el frontend las agrupa con Leaflet.markercluster.
  - **Por qué:** la base no tiene que leer, ni la red transferir, miles de filas que nadie mira; el tiempo de respuesta no depende de cuántas OT haya. Con 1.000 instalaciones pueden ser 2.000 a 3.000 OT por mes.
  - **Índices** en la migración de cada tabla paginada, sobre los campos de filtro y orden (por ejemplo, `orden_trabajo(estado, fecha_limite_sla)`).
  - **Frontend:** un componente de tabla con paginación y un hook `usePaginado` reutilizables, para no repetir la lógica en cada listado.
- Fechas en ISO 8601; zona `America/Montevideo` configurada en el backend.
- Sesión con cookie de Spring Security (Fig. 19) y protección CSRF en los pedidos que modifican datos.
- Documentación automática con springdoc-openapi (`/swagger-ui`). El frontend no inventa endpoints que no estén ahí.

**Errores**: un solo `GlobalExceptionHandler` (`@RestControllerAdvice`) que devuelve `ProblemDetail` (RFC 9457) con `type`, `title`, `status`, `detail` y, en validaciones, la lista de campos. Los servicios lanzan excepciones de negocio; nunca devuelven `null` ni códigos de error.

**Base de datos**: todo cambio de esquema por Flyway, nunca `ddl-auto=update`. Una migración aplicada no se edita: se agrega otra. Las tablas que se editan llevan `creado_en`, `modificado_en` y `version` (bloqueo optimista, para que dos usuarios no pisen la misma OT). Los registros maestros se desactivan, no se borran (1.13.1.6.1), y la bitácora solo admite inserciones (RF-21). Los datos ficticios de desarrollo van en `db/datos-prueba` como migraciones repetibles (`R__`), sin número de versión, y esa ubicación solo se activa con el perfil `dev` y en los tests: nunca se aplican en el servidor.

**Tests**

| Tipo | Herramienta | Qué cubre |
| --- | --- | --- |
| Unitario de dominio | JUnit 5 + AssertJ | Reglas y transiciones, sin Spring |
| Unitario de servicio | JUnit 5 + Mockito | Orquestación del caso de uso |
| Integración | `@SpringBootTest` + Testcontainers | Endpoint → base real, seguridad por rol |
| Arquitectura | ArchUnit | Reglas de capas y módulos |
| Frontend | Vitest + Testing Library | Hooks y componentes con lógica |

Nombre de test: `metodo_condicion_resultadoEsperado` (por ejemplo `cerrarTecnicamente_otNoIniciada_lanzaTransicionInvalida`).

**Git** (1.13.1.7, acordado el 18/10): todo cambio nace como issue en GitHub. `develop` es la rama de integración y `main` recibe `develop` al cierre de cada iteración (línea base, con etiqueta `v0.x`); las dos protegidas. Ramas desde `develop`: `feature/HU-01-login-basico` para historias y `<tipo>/<issue>-descripcion` para el resto (`fix/1-esqueleto`, `chore/3-claude-md`). Commits y títulos de PR con Conventional Commits, la HU o el área como ámbito y el issue al final (`feat(HU-01): implementar login (#12)`, `fix(docker): corregir volumen (#3)`). Todo entra por PR aprobado por el otro integrante: las tareas con squash y el cierre de iteración con merge commit (squash provisorio hasta que lo confirme Juan Pedro). El repositorio es público: nunca se suben datos reales de CIEMSA ni credenciales.

## Seguridad (HU-01)

Configuración de Spring Security elegida para RF-20 y RNF-3, verificada de punta a punta con el frontend en la prueba de concepto (#18, 28/10/2026). El contrato de la API está en el «Contrato del login (versión 2)».

**Decisión:** sesión de servidor con cookie, no JWT. La cookie HttpOnly, el vencimiento por inactividad y la protección CSRF los da Spring Security sin código propio, y el frontend nunca guarda tokens.

| Tema | Cómo quedó | Dónde |
| --- | --- | --- |
| Login | `formLogin` en `POST /api/v1/sesion`, formulario con `correo` y `contrasena`; éxito → 204, falla → `ProblemDetail`. Sin `httpBasic` ni página de login de Spring | `SecurityConfig` |
| Quién soy y logout | `GET /api/v1/sesion` (200 o 401) y `DELETE /api/v1/sesion` (204, invalida la sesión y borra las cookies) | `SesionController`, `SecurityConfig` |
| Contraseñas | BCrypt (`BCryptPasswordEncoder`, costo 10). Se guarda solo el hash; nunca se devuelve ni se guarda en la sesión (`eraseCredentials`) | `SecurityConfig`, `UsuarioAutenticado` |
| Cookie de sesión | `JSESSIONID`: HttpOnly, SameSite=Lax, 9 h sin actividad. `Secure` solo en el perfil del servidor | `application.properties` |
| CSRF | `csrf.spa()`: cookie `XSRF-TOKEN` legible por JavaScript y header `X-XSRF-TOKEN`. `CsrfCookieFilter` genera el token en cada pedido, porque Spring lo genera recién cuando alguien lo pide | `SecurityConfig`, `CsrfCookieFilter` |
| Session fixation | El login cambia el id de la sesión y renueva el token CSRF | `formLogin` (por defecto) |
| Roles | Authority `ROLE_<rol>`. Cada endpoint declara `@PreAuthorize("hasRole(...)")`, que se evalúa en cada pedido. Un cambio de rol rige desde el siguiente ingreso | Controllers |
| Bloqueo (CU-12 4c) | 5 intentos fallidos seguidos → 15 min (`sigma.seguridad.duracion-bloqueo`). Lo cuenta `IntentosIngresoListener` con los eventos de Spring Security; la regla está en `Usuario` | `IntentosIngresoListener`, `Usuario` |
| Orden de los chequeos | Bloqueo antes de la contraseña (423 aunque sea correcta); cuenta desactivada después (solo se revela con la contraseña correcta) | `SecurityConfig.authenticationProvider` |
| Errores 401 y 403 | Los errores de los filtros pasan por `ManejadorErroresSeguridad` al `GlobalExceptionHandler`: `sin-sesion`, `credenciales`, `cuenta-desactivada`, `usuario-bloqueado` (423), `sin-permiso` y `csrf` | `ManejadorErroresSeguridad` |
| Rutas públicas | `POST /api/v1/sesion`, `/api/v1/ping` y `/actuator/health`. Todo lo demás exige sesión | `SecurityConfig` |
| Usuario actual | Los servicios usan `UsuarioActual` (`id()`, `rol()`), sin depender de Spring Security | `UsuarioActualSesion` |

Reglas para lo que venga:

- Todo endpoint nuevo exige sesión por defecto. Si además depende del rol, lleva `@PreAuthorize` en la clase o en el método, y **los métodos con `@PreAuthorize` o `@Transactional` van `public`**: con métodos de paquete, el proxy puede no aplicarlos, sin ningún aviso. Cada endpoint restringido tiene un test que verifica el 403 con otro rol.
- Ningún pedido que modifica datos se excluye del CSRF, tampoco el login.
- El frontend llama siempre con el `cliente` compartido (envía la cookie y el token). Después del login **y del logout** vuelve a pedir `GET /api/v1/sesion`: así carga el estado real y recibe el token CSRF nuevo, que Spring renueva en los dos casos.
- Pendiente: `server.servlet.session.cookie.secure=true` en el perfil del servidor (deploy) y cortar la sesión de un usuario en el momento en que se lo desactiva (fuera del sprint 1).


## Frontend React

El frontend copia la idea del backend: carpetas por funcionalidad y, dentro, tres capas (llamadas a la API, hooks con la lógica y componentes que solo muestran). Una SPA con Vite + TypeScript.

```text
src/
├── app/                   # router, providers (React Query, sesión), layout por rol
├── shared/
│   ├── api/               # cliente HTTP único (cookie de sesión, CSRF, errores ProblemDetail)
│   ├── components/        # una sola librería visual: Button, Table, Modal, EstadoBadge...
│   └── lib/               # fechas, formatos
└── features/
    ├── incidentes/
    │   ├── api/           # incidentesApi.ts: registrarIncidente...
    │   ├── hooks/         # useRegistrarIncidente (React Query)
    │   ├── components/    # IncidenteForm
    │   ├── pages/         # RegistrarIncidentePage
    │   └── types.ts       # tipos que reflejan los DTOs del backend
    ├── ordenes-trabajo/   # tablero de pendientes, asignación, validación, anulación
    ├── tecnico/           # Mis OTs, iniciar, suspender, cerrar con fotos (vista móvil)
    ├── planificacion/     # semana por cuadrilla y reprogramación
    ├── activos/           # ficha, historial y mapa (Leaflet)
    ├── inventario/        # stock y movimientos
    └── administracion/    # usuarios, cuadrillas, instalaciones, importación
```

Reglas:

- Un componente **nunca llama a `fetch`/axios directo**: usa un hook, que usa la función de `api/`.
- El estado que viene del servidor vive en **React Query** (caché, recarga, estados de carga). El estado global propio se limita a la sesión.
- Un feature no importa de otro feature; lo compartido se mueve a `shared/`. Se controla con la regla `import/no-restricted-paths` de ESLint.
- Formularios con React Hook Form y Zod: validan antes del envío, con las mismas reglas que el `Request` del backend, y los mensajes dicen cómo corregir (1.13.1.6.1).
- Las acciones irreversibles (anular o validar una OT, ajustar stock) piden confirmación, y los colores de estado son los del mapa: verde, amarillo, rojo y gris (1.13.1.6.1).
- Los tipos de `types.ts` se generan desde el OpenAPI (`openapi-typescript`) cuando la API esté estable; mientras tanto se escriben a mano.
- Componentes en PascalCase (`IncidenteForm.tsx`), hooks con `use` (`useRegistrarIncidente.ts`), el resto en camelCase.
- La vista del técnico se diseña primero para celular: el formulario de cierre se conserva si falla la red y el reintento usa el mismo identificador de operación.

## Cumplimiento automático

Las reglas de capas, formato y estilo se chequean en cada PR; si la CI falla, no se mergea. Así la prolijidad no depende de acordarse.

**ArchUnit** (un test en `src/test/java/com/ciemsa/sigma/ArquitecturaTest.java`):

```java
@AnalyzeClasses(packages = "com.ciemsa.sigma", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    @ArchTest
    static final ArchRule capas = layeredArchitecture().consideringOnlyDependenciesInLayers()
        .layer("api").definedBy("..api..")
        .layer("application").definedBy("..application..")
        .layer("domain").definedBy("..domain..")
        .layer("infrastructure").definedBy("..infrastructure..")
        .whereLayer("api").mayNotBeAccessedByAnyLayer()
        .whereLayer("application").mayOnlyBeAccessedByLayers("api", "application")
        .whereLayer("infrastructure").mayOnlyBeAccessedByLayers("application");

    @ArchTest
    static final ArchRule dominioSinSpringWeb = noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "..api..");

    @ArchTest
    static final ArchRule repositoriosSoloDesdeSuModulo = classes()
        .that().haveSimpleNameEndingWith("Repository")
        .should().onlyBeAccessed().byClassesThat().resideInAPackage("..application..")
        .because("un módulo usa a otro por su servicio, no por su repositorio");

    @ArchTest
    static final ArchRule controllersNoDevuelvenEntidades = methods()
        .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
        .should().notHaveRawReturnType(BaseEntity.class);
}
```

La regla de módulos más estricta (que `inventario.application` solo use los repositorios de `inventario`) se puede agregar con Spring Modulith (`ApplicationModules.of(...).verify()`) si en el sprint 2 aparecen cruces indebidos.

**Pipeline de CI (GitHub Actions, en cada push y PR a `develop` y `main`)**

1. Backend: `./mvnw -B spotless:check verify` (formato, tests unitarios, integración con Testcontainers y, desde el sprint 2, ArchUnit).
2. Frontend: `npm ci`, `npm run format:check`, `npm run lint`, `npm test` (Vitest) y `npm run build` (el build incluye el chequeo de tipos con `tsc -b`).
3. Cobertura reportada con JaCoCo; meta orientativa 70 % en `domain` y `application`, sin bloquear el PR.

**Checklist de revisión de PR** (lo que la CI no ve):

- [ ] El caso de uso está en un servicio `application`, no en el controller.
- [ ] Las reglas de negocio están en las entidades de `domain` (o en datos, como `ReglaSLA`).
- [ ] Los nombres coinciden con la Fig. 7 y con la tabla de convenciones.
- [ ] Hay tests de la regla nueva y del endpoint, con el rol que corresponde.
- [ ] Si se tomó una decisión de arquitectura, está en la tabla de decisiones; si cambia algo del anteproyecto, quedó registrado en el capítulo 2.

## Qué se incorpora en cada sprint

Decidido el 17/10 para que la arquitectura entre en las 498 h del plan (1,6 h/PF, R1 al 50 %). Las herramientas se suman por etapas, no todas en el sprint 1. Los patrones se aplican cuando el código los pide: para la defensa alcanza con State, Fachada, Observer, Repository y DTO bien usados.

| Cuándo | Qué |
| --- | --- |
| Esqueleto (antes del 22/10) | Repositorio, Docker Compose con PostgreSQL 18, Spring Boot, Flyway, Vite + React + TS, CI que compila y corre las pruebas, Spotless y ESLint/Prettier |
| Sprint 1 | Spring Security con cookie + CSRF (HU-01), los paquetes `shared`, `administracion` y lo mínimo de `activos` (HU-02), y paginación en el primer listado |
| Sprint 2 | ArchUnit, springdoc/Swagger, Dependabot y el componente de tabla paginada reutilizable |
| Cuando haga falta | Testcontainers solo para las pruebas clave (transiciones de la OT, SLA), CD después del Taller de Deploy (27/10) y markercluster cuando llegue el mapa (HU-11) |

Los módulos no se crean vacíos de entrada: cada paquete aparece cuando una historia lo necesita. Al cierre del sprint 1 (04/11) se mide el desvío; si pasa el 15 %, se simplifica más o se aplica el orden de recorte.

## Qué evitar y registro de decisiones

**Sobreingeniería que no hacemos** (cuesta horas y no mejora la nota ni el producto):

- Interfaz + `Impl` para servicios con una sola implementación.
- Repositorios genéricos propios sobre Spring Data.
- Arquitectura hexagonal completa con puertos y adaptadores para todo; solo los puertos de la sección de interfaces.
- Modelo de dominio separado del modelo JPA.
- Microservicios, colas de mensajes o CQRS.
- Capas "por si acaso" (`Manager`, `Helper`, `Util` con lógica de negocio).

**Atajos que tampoco hacemos** (la otra punta):

- Lógica de negocio en controllers o en componentes React.
- Entidades JPA devueltas por la API.
- `setEstado(...)` desde un servicio.
- Cambios de esquema sin migración Flyway.
- Llamadas HTTP directas desde componentes.

**Decisiones de arquitectura.** No se escriben ADR formales: las decisiones grandes ya están justificadas en el anteproyecto y los cambios van a la tabla «Cambios respecto del anteproyecto» del capítulo 2. Esta tabla solo dice dónde está cada una:

| Decisión | Justificada en |
| --- | --- |
| Monolito modular, estructura completa solo en `ot` | Anteproyecto 1.11.1 y Fig. 19; esta guía (17/10) |
| Spring Security con usuarios propios, sin Keycloak | Anteproyecto 1.11.4 |
| Una SPA React adaptable, sin app nativa | Anteproyecto 1.11.1 y 1.11.4 |
| PostgreSQL como única base, migraciones con Flyway | Anteproyecto 1.11.1 y Tabla 16 |
| Eventos de dominio para la bitácora y efectos entre módulos | Anteproyecto Anexo A.2 |
| Fotos detrás de `AlmacenArchivos` | Anteproyecto Fig. 5 y Fig. 19 |
| Java 25 | Anteproyecto V16 (Tabla 16) |
| Listados paginados | Esta guía, API REST (17/10) |

| Sesión de servidor con cookie HttpOnly + CSRF (`formLogin`, `csrf.spa()`), sin JWT | Anteproyecto 1.11.4 y Fig. 19; esta guía, «Seguridad (HU-01)», verificado en #18 |
| Lombok en el backend (`@Getter`, `@RequiredArgsConstructor`; sin `@Data` ni `@Setter` en entidades) | Esta guía (#19) |

Si aparece una decisión nueva que no esté en el anteproyecto, se agrega una fila y, si cambia algo de lo entregado, una fila en la tabla de cambios del capítulo 2.
