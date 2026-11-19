# Modelo de datos de la rama auditada

- maquinas: ID de equipo global, planta, horas operativas declaradas, conteo de
  fallas, MTBF nullable, contador de bloqueos, estado digital y baja lógica.
- tecnicos: identidad individual, nombre, hash BCrypt, rol, planta y baja lógica.
  UNIQUE(planta_id,username). Las contraseñas/hashes nunca salen por la API.
- fallas: referencia única por planta/máquina. Cada técnico de una misma falla
  enlaza este registro; no crea otra falla por participar. No se reutiliza una
  referencia para una avería nueva.
- bloqueos_loto: ticket con técnico, máquina, falla opcional, declaración de
  candado, motivo, marcas temporales, notas cifradas y transición ACTIVO→CERRADO.
  No se borran tickets. tecnico_id/falla_id pueden ser null en datos legados;
  las altas nuevas siempre tienen responsable y las correctivas una falla.
- eventos_loto: CREADO/LIBERADO/LEGADO_IMPORTADO, identidad, ticket y fecha;
  append-only. No contiene credenciales ni notas confidenciales.

Las claves foráneas compuestas incluyen planta para impedir referencias cruzadas.
Los índices cubren catálogo, filtros de bloqueos, responsable activo y eventos.
RLS se aplica a las cinco tablas; SET LOCAL fija la planta en cada transacción.
El usuario de la API no es propietario/superusuario ni tiene BYPASSRLS.

Los diagramas/PDF del Hito 1 se conservan como entregables históricos. El esquema
vigente está en schema.sql y migrations/V2__integridad_loto.sql. El SQL no elimina
tablas ni realiza deduplicaciones históricas inferidas. El estado digital y la
declaración del técnico no prueban aislamiento físico ni energía cero.
