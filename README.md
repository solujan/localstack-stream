# LocalStack + DynamoDB Streams + AWS Lambda + Spring Boot

Proyecto de ejemplo para probar localmente un flujo **event-driven**
completo usando Spring Boot, DynamoDB, DynamoDB Streams, AWS Lambda,
LocalStack y Testcontainers.

## Objetivo

``` text
POST /users
    ↓
Spring Boot
    ↓
DynamoDB: users
    ↓
DynamoDB Stream
    ↓
Event Source Mapping
    ↓
AWS Lambda
    ↓
DynamoDB: processed_users
```

El integration test ejecuta `POST /users` y verifica de forma asíncrona
con Awaitility que la Lambda procesó el evento y creó el usuario en
`processed_users` con `status = PROCESSED`.

## Tecnologías

-   Java 17+ (Maven también fue ejecutado con Java 21)
-   Spring Boot
-   AWS SDK for Java v2
-   DynamoDB / DynamoDB Streams
-   AWS Lambda
-   LocalStack + Docker
-   Testcontainers
-   JUnit 5
-   Awaitility
-   Maven

## Dependencias Maven

Se usa el BOM de AWS SDK v2 y el BOM de Testcontainers:

``` xml
<properties>
    <java.version>17</java.version>
    <aws.sdk.version>2.32.24</aws.sdk.version>
    <testcontainers.version>2.0.5</testcontainers.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>${aws.sdk.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-bom</artifactId>
            <version>${testcontainers.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Dependencias principales: `software.amazon.awssdk:dynamodb`,
`dynamodbstreams`, `lambda`, `com.amazonaws:aws-lambda-java-core` y
`aws-lambda-java-events`.

Para tests: `testcontainers`, `testcontainers-localstack`,
`testcontainers-junit-jupiter` y `awaitility`.

## LocalStack

LocalStack se usa como implementación local de los servicios AWS.

``` bash
aws --endpoint-url=http://localhost:4566 dynamodb list-tables --region us-east-1
```

No hace falta `aws login`; sí conviene especificar siempre una región
como `us-east-1`.

Para los integration tests fijamos una versión concreta en vez de
`latest`:

``` java
@Container
static final LocalStackContainer LOCALSTACK =
        new LocalStackContainer(
                DockerImageName.parse("localstack/localstack:4.8.1")
        );
```

Esto hace el test más reproducible.

## Tabla `users` y DynamoDB Stream

La tabla principal es `users`, con `id` String como partition key. El
stream está habilitado con `NEW_AND_OLD_IMAGES`.

Para obtener su ARN:

``` bash
aws --endpoint-url=http://localhost:4566   dynamodb describe-table   --table-name users   --region us-east-1   --query "Table.LatestStreamArn"   --output text
```

## Tabla `processed_users`

La Lambda deja un efecto observable escribiendo en una segunda tabla:

``` text
processed_users
```

El resultado esperado es:

``` json
{
  "id": "2001",
  "name": "Sofia",
  "email": "sofia@test.com",
  "status": "PROCESSED"
}
```

Esto permite testear el procesamiento asíncrono sin depender de logs.

## Lambda

La Lambda implementa:

``` java
RequestHandler<DynamodbEvent, Void>
```

y procesa solamente eventos `INSERT`.

``` java
for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
    if (!"INSERT".equals(record.getEventName())) {
        continue;
    }
    process(record.getDynamodb());
}
```

Lee `NewImage`, extrae `id`, `name` y `email`, y escribe el usuario en
`processed_users` agregando:

``` text
status = PROCESSED
```

### Dos tipos de `AttributeValue`

Los eventos usan:

``` text
com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue
```

mientras que `DynamoDbClient` usa:

``` text
software.amazon.awssdk.services.dynamodb.model.AttributeValue
```

## Endpoint AWS dentro de la Lambda

Dentro del runtime Lambda de LocalStack no se debe asumir que
`localhost:4566` apunta al LocalStack principal.

La Lambda obtiene:

``` java
String endpoint = System.getenv("AWS_ENDPOINT_URL");
```

y construye su `DynamoDbClient` usando ese endpoint.

## Packaging de la Lambda

El primer JAR superaba los 100 MB porque Spring Boot lo estaba
reempaquetando como Boot JAR.

Se deshabilitó el repackage:

``` xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <skip>true</skip>
    </configuration>
</plugin>
```

La Lambda se empaqueta con `maven-shade-plugin`.

No conviene usar un `artifactSet` con `includes` demasiado restrictivo.
Eso produjo errores como:

``` text
ClassNotFoundException: org.joda.time.DateTime
ClassNotFoundException: software.amazon.awssdk.identity.spi.AwsCredentialsIdentity
ClassNotFoundException: software.amazon.awssdk.protocols.json.internal.unmarshall.SdkClientJsonProtocolAdvancedOption
```

La estrategia final fue incluir las dependencias transitivas runtime
necesarias y excluir principalmente Spring y librerías de test.

## Crear y actualizar la Lambda

Crear:

``` bash
aws --endpoint-url=http://localhost:4566   lambda create-function   --function-name dynamodb-stream-handler   --runtime java17   --handler org.solujan.localstackstream.lambda.DynamoStreamHandler::handleRequest   --role arn:aws:iam::000000000000:role/lambda-role   --zip-file fileb://target/lambda.jar   --region us-east-1
```

Actualizar:

``` bash
aws --endpoint-url=http://localhost:4566   lambda update-function-code   --function-name dynamodb-stream-handler   --zip-file fileb://target/lambda.jar   --region us-east-1
```

## Event Source Mapping

DynamoDB no invoca directamente la Lambda.

``` text
DynamoDB
   ↓
DynamoDB Stream
   ↓
Event Source Mapping
   ↓
Lambda
```

El Event Source Mapping funciona como un poller administrado que lee el
stream, forma batches e invoca la función.

``` bash
aws --endpoint-url=http://localhost:4566   lambda create-event-source-mapping   --function-name dynamodb-stream-handler   --event-source-arn <STREAM_ARN>   --starting-position LATEST   --batch-size 1   --region us-east-1
```

`LATEST` procesa registros nuevos. `TRIM_HORIZON` comienza desde los
registros más antiguos todavía disponibles.

## Prueba manual end-to-end

Insertar:

``` bash
aws --endpoint-url=http://localhost:4566   dynamodb put-item   --table-name users   --item '{"id":{"S":"2001"},"name":{"S":"Sofia"},"email":{"S":"sofia@test.com"}}'   --region us-east-1
```

Verificar:

``` bash
aws --endpoint-url=http://localhost:4566   dynamodb get-item   --table-name processed_users   --key '{"id":{"S":"2001"}}'   --region us-east-1
```

Resultado comprobado: el registro apareció con `status = PROCESSED`.

## REST API

El endpoint Spring es:

``` text
POST /users
```

Request:

``` json
{
  "id": "3001",
  "name": "Sofia",
  "email": "sofia@test.com"
}
```

`UserController` delega en `UserService`, que ejecuta un `PutItem` sobre
`users`.

El `DynamoDbClient` de Spring usa una property configurable:

``` properties
aws.endpoint=http://localhost:4566
```

En los tests se reemplaza por el endpoint dinámico de Testcontainers
usando `@DynamicPropertySource`.

## Integration tests con Testcontainers

La clase base usa:

``` java
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
```

y registra:

``` java
@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add(
            "aws.endpoint",
            () -> LOCALSTACK.getEndpoint().toString()
    );
}
```

El setup crea automáticamente:

``` text
LocalStack
   ├── users + Stream
   ├── processed_users
   ├── Lambda
   └── Event Source Mapping
```

Los clientes `DynamoDbClient` y `LambdaClient` apuntan a
`LOCALSTACK.getEndpoint()`.

## Integration test E2E

Spring se levanta en un puerto aleatorio:

``` java
@LocalServerPort
int port;
```

El test hace el POST real:

``` java
var restClient = RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .build();

restClient.post()
        .uri("/users")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""
            {
              "id": "3001",
              "name": "Sofia",
              "email": "sofia@test.com"
            }
            """)
        .retrieve()
        .toBodilessEntity();
```

Como DynamoDB Stream + Lambda son asíncronos, el assert usa Awaitility:

``` java
await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> {
            var response = dynamoDbClient.getItem(
                    GetItemRequest.builder()
                            .tableName("processed_users")
                            .key(Map.of(
                                    "id",
                                    AttributeValue.fromS("3001")
                            ))
                            .build()
            );

            assertThat(response.hasItem()).isTrue();
            assertThat(response.item().get("status").s())
                    .isEqualTo("PROCESSED");
        });
```

No usar `Thread.sleep()` para esperar el procesamiento.

El test valida realmente:

``` text
JUnit
  ↓
Spring Boot
  ↓
POST /users
  ↓
UserController
  ↓
UserService
  ↓
DynamoDB users
  ↓
DynamoDB Stream
  ↓
Event Source Mapping
  ↓
Lambda
  ↓
DynamoDB processed_users
  ↓
Awaitility
  ↓
assert status == PROCESSED
```

## Lambda en otro repositorio

Si la Lambda vive en otro repo, el test Java no debería hacer
`git clone` ni ejecutar Maven.

Separar:

``` text
Build / preparación
    ↓
obtiene el repo Lambda
    ↓
construye lambda.jar
    ↓
target/integration/dynamodb-stream-lambda.jar
    ↓
JUnit lo despliega en LocalStack
```

Para un playground se puede usar un script que clone un commit/tag
específico, ejecute `mvn clean package -DskipTests` y copie el
artefacto.

Para CI/CD real, el orden recomendado es:

1.  Artefacto versionado publicado por el pipeline de la Lambda.
2.  Checkout de un tag/commit específico.
3.  `git clone` de `main` solo como última opción.

El test debería conocer únicamente la ruta del deployment artifact, no
cómo fue construido.

## Troubleshooting encontrado

### `RequestEntityTooLargeException`

El deployment artifact era demasiado grande porque Spring Boot lo
reempaquetaba. Se solucionó separando el packaging Lambda del Boot JAR.

### `ClassNotFoundException`

Los `includes` manuales del Shade plugin estaban eliminando dependencias
transitivas. Se corrigió empaquetando el árbol runtime necesario.

### `FunctionError: Unhandled`

El Stream y Event Source Mapping funcionaban, pero la Lambda fallaba
durante init/runtime. Los errores de clases faltantes permitieron
identificar el problema de packaging.

### `ExpiredIteratorException`

LocalStack mostró que el shard iterator había expirado y luego lo
reinicializó. El poller se recuperó automáticamente.

### CloudWatch Logs deshabilitado

Si `logs` no está habilitado, `aws logs tail` falla. Para diagnóstico se
puede usar `docker logs <container>` o invocar manualmente la Lambda.

### Mockito self-attaching

El warning de Mockito/ByteBuddy no era la causa del fallo del test.

### Maven Surefire

Se observó un caso donde el test terminaba correctamente pero Surefire
fallaba manejando el fork. Para aislarlo:

``` bash
mvn -Dtest=LocalStackSmokeTest -DforkCount=0 test
```

## Arquitectura final

``` text
┌────────────────────┐
│   Spring Boot API  │
│    POST /users     │
└─────────┬──────────┘
          │
          ▼
┌────────────────────┐
│ DynamoDB: users    │
└─────────┬──────────┘
          │
          ▼
┌────────────────────┐
│ DynamoDB Stream    │
└─────────┬──────────┘
          │
          ▼
┌────────────────────┐
│ Event Source       │
│ Mapping            │
└─────────┬──────────┘
          │
          ▼
┌────────────────────┐
│ AWS Lambda         │
│ DynamoStreamHandler│
└─────────┬──────────┘
          │
          ▼
┌────────────────────┐
│ processed_users    │
│ status=PROCESSED   │
└────────────────────┘
```

El resultado es un integration test local de extremo a extremo que
prueba REST + DynamoDB + Streams + Lambda usando infraestructura efímera
con Testcontainers.
