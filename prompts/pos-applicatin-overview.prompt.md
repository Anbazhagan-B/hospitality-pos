Design a Point of Sale (POS) application using Spring Boot, AOP, and an ingress controller. The system must include the following microservices:

1. **Check Service**: Handles check processing.
2. **Employee Service**: Manages employee data and authentication.
3. **Payment Gateway Service**: Processes credit card, loyalty, gift card, and casino tender payments.
4. **Kitchen Display Service**: Manages kitchen order displays.
5. **Enterprise Management Service**: Handles enterprise data, profit centers, tenders, menu items, and terminal data.
6. **Admin Panel Service**: Prepares and manages data for admin-facing forms.
7. **Cook Service**: Prepares and serves terminal data as JSON for downloads.

**User Interfaces:**

- Terminal UI for point-of-sale operations.
- Admin Panel UI for system administration.

**System Requirements:**

- Use an ingress controller with JWT authentication and rate limiting.
- Implement circuit breaking for service resilience.
- Apply Aspect-Oriented Programming (AOP) for cross-cutting concerns.
- Integrate Spring Boot Actuator with Prometheus and Grafana for monitoring.
- Use Kafka for event-driven communication with the Kitchen Display Service.

**Constraints:**

- Ensure all services are stateless and independently deployable.
- Follow best practices for microservices architecture.
- Reference: [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/), [Ingress Controllers](https://kubernetes.io/docs/concepts/services-networking/ingress-controllers/), [Spring Cloud Circuit Breaker](https://spring.io/projects/spring-cloud-circuitbreaker), [Prometheus Monitoring](https://prometheus.io/docs/introduction/overview/), [Kafka Integration](https://spring.io/projects/spring-kafka).

Provide a high-level architecture diagram and a brief description of the interaction flow between services.
