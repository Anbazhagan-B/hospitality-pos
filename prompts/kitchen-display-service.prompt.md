Design and implement a Kitchen Display Service with the following specifications:

- **Order Management**: Create RESTful APIs to handle kitchen orders, supporting creation, update, and retrieval of order data. Ensure endpoints follow standard REST conventions and validate input.
- **Kafka Integration**: Integrate with Apache Kafka to consume order events from the Check Service. Implement robust error handling and message acknowledgment.
- **Real-time Updates**: Enable real-time notifications for kitchen staff when orders are placed or updated, using technologies such as WebSocket or Server-Sent Events.
- **Monitoring**: Use Spring Boot Actuator to provide health checks, metrics, and monitoring endpoints. Ensure these endpoints are secured and documented.
- **API Documentation**: Document all API endpoints, including request/response schemas, authentication requirements, and error codes, using OpenAPI/Swagger.
- **Constraints**: The service must be implemented in Java using Spring Boot. Follow best practices for code structure, exception handling, and logging.

Reference: [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/), [Kafka Integration Guide](https://docs.spring.io/spring-kafka/docs/current/reference/html/), [OpenAPI Specification](https://swagger.io/specification/).
