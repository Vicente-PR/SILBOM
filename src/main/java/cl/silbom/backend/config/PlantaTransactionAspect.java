package cl.silbom.backend.config;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * QUÉ HACE: Configura el contexto de planta antes de ejecutar un método @Transactional interceptado
 * por Spring en el backend.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El Control 4 se apoya en FORCE ROW LEVEL SECURITY de las cinco
 * tablas. SELECT set_config con tercer parámetro true equivale a SET LOCAL: limita el contexto a la
 * transacción y evita reutilizarlo al devolver la conexión a
 * HikariCP. @EnableTransactionManagement(order = 0) abre la transacción antes del
 * aspecto @Order(1). No cambia la planta a partir del JSON del cliente.
 */
@Aspect
@Component
@Order(1)
@EnableTransactionManagement(order = 0)
public class PlantaTransactionAspect {

    private final EntityManager entityManager;

    private final String planta;

    public PlantaTransactionAspect(
            EntityManager entityManager, @Value("${silbom.security.planta-id}") String planta) {
        this.entityManager = entityManager;
        this.planta = planta;
    }

    /**
     * QUÉ HACE: Fija app.current_planta_id en la misma conexión de Hibernate y continúa la
     * operación.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: La consulta es parametrizada; solo se ejecuta en PostgreSQL. H2
     * permite pruebas de dominio, pero no demuestra que RLS funcione.
     */
    @Around(
            "execution(* cl.silbom.backend..*(..)) &&"
                + " @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object ejecutar(ProceedingJoinPoint call) throws Throwable {
        entityManager
                .unwrap(Session.class)
                .doWork(
                        connection -> {
                            if ("PostgreSQL"
                                    .equals(connection.getMetaData().getDatabaseProductName())) {
                                try (var statement =
                                        connection.prepareStatement(
                                                "SELECT set_config('app.current_planta_id', ?,"
                                                    + " true)")) {
                                    statement.setString(1, planta);
                                    statement.execute();
                                }
                            }
                        });
        return call.proceed();
    }
}
