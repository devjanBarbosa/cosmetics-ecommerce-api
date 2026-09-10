# Cosmetics E-Commerce Platform - Backend API

## 📌 Overview
A robust, enterprise-grade RESTful API built to power a cosmetics retail platform. Developed with Java and Spring Boot, this backend handles complex business logic including secure authentication, inventory management, and cloud-based file storage.

## ⚙️ Core Architecture & Features
Based on the repository structure, this system implements several advanced backend patterns:
* **Stateless Security:** JWT-based authentication flow utilizing custom security filters and user details services (`SecurityFilter.java`, `TokenService.java`).
* **Cloud Infrastructure:** Native Amazon S3 integration for scalable product image storage and retrieval (`AwsConfig.java`, `S3Service.java`).
* **Modern Payment Processing:** Integrated Brazilian Pix payment generation and automated webhook listeners for real-time transaction updates (`PixService.java`, `WebhookController.java`).
* **Business Intelligence:** Custom analytics services providing daily sales reports and low stock alerts (`AnalyticsService.java`, `VendasDiariasDTO.java`, `ProdutoEstoqueBaixoDTO.java`).
* **Third-Party Integrations:** Automated postal code (CEP) calculations for shipping logistics and Google Reviews aggregation (`CepController.java`, `GoogleReviewsService.java`).
* **SEO Optimization:** Dynamic XML sitemap generation to ensure proper search engine indexing of the product catalog (`SitemapService.java`, `UrlSet.java`).

## 🛠️ Tech Stack
* **Core Framework:** Java, Spring Boot, Spring Security
* **Build Tool:** Maven (`pom.xml`)
* **Cloud Provider:** AWS (S3 configuration)
* **Architecture:** MVC Pattern, Data Transfer Objects (DTOs) for payload encapsulation, and Criteria API for dynamic database querying (`ProdutoSpecification.java`).

## 🚀 How to Run Locally

### Prerequisites
* Java (JDK 17 or higher recommended)
* Maven (or use the included `mvnw` wrapper)
* Relational Database (configured via properties)

### Setup
1. Clone the repository to your local machine.
2. Configure your database credentials and AWS S3 keys in `src/main/resources/application-dev.properties`.
3. Execute the application using the Maven wrapper: `./mvnw spring-boot:run`.
4. The REST API will initialize and accept requests on the configured localhost port.
