# Post-contenido — Unidad 8: Patrones Arquitectónicos II

## Descripción

Repositorio del post-contenido de la Unidad 8 de Patrones de Diseño de Software — Sexto Semestre. Sistema de seguimiento de hallazgos de auditoría interna implementado con **Clean Architecture** (Parte 1) y extendido con un **dashboard agregado** y una **bitácora de trazabilidad** (Parte 2), sobre el mismo proyecto Spring Boot. La Parte 2 no crea un proyecto paralelo: parte de un análisis costo-beneficio de CQRS/Event Sourcing y solo implementa lo que ese análisis justifica.

---

## Parte 1 — Clean Architecture (Hallazgos de Auditoría)

El proyecto organiza los cuatro círculos concéntricos de Clean Architecture:

| Círculo | Paquete | Contenido |
|---|---|---|
| **Entities** | `domain/` | Aggregate Root `HallazgoAuditoria`; Value Objects `HallazgoId`, `Severidad`, `EstadoHallazgo` (máquina de estados), `PlanRemediacion`; `TransicionInvalidaException`. |
| **Use Cases** | `usecase/` | Interfaces de casos de uso (registrar, iniciar remediación, cerrar, reabrir, consultar, dashboard), puertos en `port/` e implementaciones en `impl/`. |
| **Interface Adapters** | `adapter/` | `in/web/` (`HallazgoController` y DTOs) y `out/persistence/` (`HallazgoJpaEntity`, `HallazgoJpaRepository`, `HallazgoRepositoryAdapter`, adaptador del historial). |
| **Frameworks & Drivers** | Spring Boot + JPA + H2 | Cableado explícito en `config/AuditoriaConfiguration`. |

La dependencia del código siempre apunta hacia adentro, hacia `domain/`. Los paquetes `domain/` y `usecase/` no importan `org.springframework` ni `jakarta.persistence`: la entidad JPA vive solo en `adapter/out/persistence/` y el adaptador traduce campo a campo entre ella y el objeto de dominio. Gracias a esto, `HallazgoAuditoria` puede instanciarse y probarse con JUnit sin `@SpringBootTest`.

### Estructura de paquetes

```
auditoria-hallazgos/
├── pom.xml
└── src/main/java/com/example/auditoria/
    ├── domain/
    │   ├── entity/HallazgoAuditoria.java          ← Aggregate Root
    │   └── valueobject/
    │       ├── HallazgoId.java
    │       ├── Severidad.java                     ← enum simple
    │       ├── EstadoHallazgo.java                ← enum con máquina de estados
    │       ├── PlanRemediacion.java               ← Value Object inmutable
    │       └── TransicionInvalidaException.java
    ├── usecase/
    │   ├── *UseCase.java                          ← interfaces de entrada
    │   ├── port/                                  ← puertos de salida y vistas
    │   │   ├── HallazgoRepositoryPort.java
    │   │   ├── HistorialAuditoriaPort.java
    │   │   ├── ConteoCategoria.java / PromedioCategoria.java
    │   │   ├── DashboardAuditoriaView.java
    │   │   └── CambioEstadoView.java
    │   └── impl/*Service.java
    ├── adapter/
    │   ├── in/web/ (HallazgoController, dto/)
    │   └── out/persistence/
    │       ├── HallazgoJpaEntity / HallazgoJpaRepository / HallazgoRepositoryAdapter
    │       └── HistorialCambioEstado* / HistorialAuditoriaAdapter
    ├── config/AuditoriaConfiguration.java
    └── AuditoriaHallazgosApplication.java
```

### Endpoints

| Método | Ruta | Resultado |
|---|---|---|
| POST | `/api/hallazgos` | 201 + `hallazgoId` (UUID) |
| PATCH | `/api/hallazgos/{id}/iniciar-remediacion` | 200, estado `EN_REMEDIACION` |
| PATCH | `/api/hallazgos/{id}/cerrar` | 200, estado `CERRADO` (400 si la transición es inválida o no hay plan) |
| PATCH | `/api/hallazgos/{id}/reabrir` | 200, estado `REABIERTO` |
| GET | `/api/hallazgos` y `/api/hallazgos/{id}` | Consulta de hallazgos |
| GET | `/api/hallazgos/dashboard` | Conteos por severidad y estado, y promedio de días de cierre por área |
| GET | `/api/hallazgos/{id}/historial` | Cambios de estado en orden cronológico |


## Parte 2 — Análisis costo-beneficio de CQRS/Event Sourcing

**Contexto.** El comité de auditoría plantea dos requisitos: (1) un dashboard consolidado antes de cada reunión mensual (hallazgos por severidad, por estado y promedio de días entre detección y cierre por área) y (2) trazabilidad legal de cada cambio de estado (quién, cuándo, de qué estado a cuál), sin alteración retroactiva. Son el tipo de requisitos con los que la guía introduce CQRS (lecturas con forma distinta a las escrituras) y Event Sourcing (historial completo para auditoría). Antes de escribir código, se aplicaron los criterios de la Sección 7 (y la 4.4 y 5.5) de la guía a la escala real del proyecto.


---

## Decisiones de diseño

### 1. Severidad como enum simple vs. EstadoHallazgo como enum con máquina de estados

**Decisión:** `EstadoHallazgo` es un enum con comportamiento (`puedeTransicionarA(destino)`); `Severidad` es un enum simple de cuatro valores (`CRITICA`, `ALTA`, `MEDIA`, `BAJA`).

**Justificación:** la diferencia está en si el concepto encapsula una **regla de negocio real**. `EstadoHallazgo` sí: define qué transiciones son válidas (`ABIERTO → EN_REMEDIACION → CERRADO → REABIERTO → EN_REMEDIACION`) y rechaza cualquier otra (por ejemplo, cerrar un hallazgo que nunca estuvo en remediación o reabrir uno que sigue abierto). Ubicar esa regla dentro del propio enum respeta el encapsulamiento de DDD: la regla vive en el objeto que la conoce, el Aggregate Root solo delega en ella desde `transicionar(...)`, y cualquier cambio del ciclo de vida se modifica en un único lugar, con una sola prueba unitaria. `Severidad`, en cambio, es una clasificación estática: ninguna severidad es «más válida» que otra en un momento dado ni condiciona ninguna operación del sistema. Dotarla de métodos (orden, comparaciones, umbrales) sería inventar comportamiento que el negocio no ha pedido, es decir, sobre-diseño. Tampoco habría sido razonable lo contrario (un estado como enum simple), porque entonces la validación de transiciones se dispersaría en `if` dentro de los casos de uso o del agregado, y la regla de negocio quedaría fuera del dominio o duplicada. Si en el futuro la severidad influyera en el negocio (por ejemplo, plazos máximos de remediación según severidad), ese sería el momento de darle comportamiento.

### 2. PlanRemediacion como Value Object embebido vs. agregado separado

**Decisión:** `PlanRemediacion` es un Value Object inmutable (un `record`) embebido en el Aggregate Root `HallazgoAuditoria`, no un agregado independiente con su propio repositorio.

**Justificación:** el criterio determinante es el **límite de consistencia transaccional** de los Agregados (Sección 3.3 de la guía): un agregado debe contener todo lo que debe ser consistente de forma inmediata dentro de una misma transacción. Aquí existen dos invariantes que involucran al plan: un hallazgo **no puede pasar a `EN_REMEDIACION` sin un plan válido** (`iniciarRemediacion(plan)` exige el plan y lo valida) y **no puede cerrarse sin uno ya definido** (`cerrar()` lanza `IllegalStateException` si no existe). Si el plan fuera un agregado separado referenciado por `HallazgoId`, ambas invariantes cruzarían dos agregados y dos repositorios; su cumplimiento dependería de coordinar dos escrituras, abriendo ventanas de inconsistencia (hallazgo en remediación sin plan persistido, o plan huérfano sin hallazgo). Al embeberlo, `HallazgoAuditoria` es el único guardián de las reglas y todo se persiste de forma atómica. Además, el plan cumple las características de un Value Object: no tiene identidad propia (se define por responsable, fecha límite y notas), es inmutable y no tiene ciclo de vida independiente del hallazgo. En persistencia se aplana en tres columnas (`planResponsable`, `planFechaLimite`, `planNotas`), sin tabla ni repositorio adicionales.

### 3. CQRS/Event Sourcing completos vs. extensión liviana del repositorio existente

**Decisión:** se implementó una **extensión liviana**: se ampliaron `HallazgoRepositoryPort` y `HallazgoJpaRepository` (el mismo puerto y el mismo repositorio) con tres consultas agregadas, y no se crearon stacks de comando y consulta separados ni persistencia por eventos.

**Justificación (criterios de la Sección 7 y 4.4 de la guía, desarrollados en el análisis de la Parte 2):**

- **Escala y carga:** un desarrollador, sin usuarios concurrentes reales y sin asimetría entre lecturas y escrituras; no hay nada que escalar por separado.
- **Complejidad de consultas:** los tres indicadores del dashboard son consultas JPQL con `GROUP BY` sobre un único esquema; no requieren otra tecnología ni un modelo desnormalizado.
- **Consistencia:** el comité consulta el dashboard bajo demanda; leer de la fuente de verdad lo hace inmediatamente consistente, mientras que CQRS completo introduciría consistencia eventual sin necesidad.
- **Trazabilidad:** basta con una bitácora cronológica, no se exige reproducir estados (ver decisión 4).
- **Señales de sobre-ingeniería (7.2):** varias están presentes o se agravarían (más mappers, curva de aprendizaje, ausencia de experto de negocio).

Los dos requisitos nuevos se cubren con extensiones puntuales que respetan los círculos de Clean Architecture: el dashboard es un caso de uso (`ObtenerDashboardAuditoriaUseCase`) que depende del puerto existente, y las proyecciones son un detalle del adaptador de persistencia. El costo de la alternativa (dos modelos que mantener sincronizados) no es proporcional al problema. Una conclusión distinta habría exigido una razón concreta y verificable; no la hay hoy, y las condiciones que la crearían se describen en las Conclusiones.

### 4. Bitácora simple (HistorialCambioEstado) vs. Event Store completo

**Decisión:** la trazabilidad se implementa como una **tabla de auditoría append-only** (`historial_cambios_estado`, entidad `HistorialCambioEstadoJpaEntity`) escrita en la **misma transacción** que cada transición de estado, que coexiste con —y no reemplaza a— el estado actual de `HallazgoJpaEntity`, que sigue siendo la única fuente de verdad.

**Justificación (señales de sobre-ingeniería, Sección 7.2):** un Event Store completo obligaría a que `HallazgoAuditoria` dejara de persistir su estado actual y se reconstruyera por *replay* de eventos en cada lectura: un cambio de fondo sobre un agregado que ya funciona, sin una necesidad real de reproducir estados intermedios ni de alimentar proyecciones desconocidas. Frente a las señales de la guía:

1. **Mappers y adaptadores sobre lógica de negocio:** un Event Store añadiría serialización de eventos, versionado y rehidratación, aumentando la parte «de infraestructura» del código frente a la lógica real.
2. **Más tiempo explicando que entregando:** el equipo (una persona) no tiene experiencia previa con Event Sourcing, y su adopción desplazaría la entrega de funcionalidad.
3. **Pruebas de dominio más difíciles:** hoy `HallazgoAuditoria` se prueba con JUnit puro llamando a `cerrar()`; con eventos habría que probar la aplicación de cada evento y la reconstrucción del estado.
4. **Sin experto de negocio:** no hay con quién modelar los eventos de dominio, y los eventos mal definidos son difíciles de corregir porque son inmutables.
5. **Requerimientos de auditoría, modelos de lectura y escala:** sí hay un requisito de auditoría, pero es acotado (mostrar la secuencia de cambios), no hay múltiples modelos de lectura ni escala diferencial.

La bitácora solo necesita mostrar la secuencia de cambios (estado anterior, estado nuevo, motivo y fecha); no necesita ser la fuente de verdad. Es append-only por diseño (`registrar` solo inserta y no existe operación de actualización ni de borrado), y al escribirse en la misma transacción que la transición, nunca hay cambio de estado sin registro ni registro de un cambio que no ocurrió. Cada transición exitosa (`iniciar-remediacion`, `cerrar`, `reabrir`) inserta exactamente un registro.

---

## Cómo ejecutar

```
$ mvn clean package
$ mvn spring-boot:run
```

```

## Herramientas utilizadas

- Java 17, Spring Boot 3.x, Spring Data JPA, H2
- Apache Maven, Postman/curl, Git, GitHub

## Conclusiones
La Parte 1 mostró que Clean Architecture mantiene las reglas de negocio (la máquina de estados y las invariantes del plan de remediación) aisladas de Spring y JPA, lo que permite probarlas sin framework y cambiar la persistencia sin tocar el dominio. La Parte 2 mostró que un requisito que *parece* pedir CQRS o Event Sourcing no necesariamente los justifica: aplicando los criterios de escala, complejidad de consultas, consistencia, trazabilidad y señales de sobre-ingeniería, bastaron consultas agregadas sobre el mismo repositorio y una bitácora append-only. Reconsideraría esta decisión si el sistema creciera hasta tener muchos usuarios concurrentes con una asimetría clara entre lecturas y escrituras, si el dashboard se volviera lento sobre grandes volúmenes o necesitara múltiples modelos de lectura distintos, si Cumplimiento exigiera reconstruir estados pasados completos o garantías de inalterabilidad más fuertes que una tabla append-only (por ejemplo, registros encadenados con hash), o si existieran un experto de negocio y un equipo con experiencia para modelar eventos. Mientras esas condiciones no existan, la solución más simple que cumple los requisitos es también la más correcta.
