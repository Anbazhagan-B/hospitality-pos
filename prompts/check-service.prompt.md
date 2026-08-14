Design and implement a Check Service with the following requirements:

- Process check-related operations and expose RESTful APIs for creating, updating, and retrieving check information.
- Raise events to the Kitchen Display Service via Kafka when orders are placed or updated.
- Integrate with the Payment Gateway Service to process payments associated with checks.
- Utilize Spring Boot Actuator for monitoring and health checks.
- Implement circuit breaking to ensure resilience against failures in dependent services.
- Ensure the solution follows best practices for microservices architecture and includes appropriate error handling and logging.

Refer to the [Spring Boot Actuator documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html) and [Resilience4j circuit breaker documentation](https://resilience4j.readme.io/docs/circuitbreaker) for implementation details.
