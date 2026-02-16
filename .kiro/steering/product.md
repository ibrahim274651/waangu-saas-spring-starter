# Product Overview

Waangu 360 SaaS Spring Boot Starter is a multi-tenant hybrid starter library for Waangu 360 ERP microservices.

## Purpose

Provides foundational multi-tenancy infrastructure for Spring Boot microservices, supporting three tenant isolation modes:
- POOLED: Shared database with row-level security
- SCHEMA: Dedicated PostgreSQL schema per tenant
- DEDICATED_DB: Separate database per tenant

## Core Capabilities

- JWT-based tenant context extraction and validation
- Tenant-aware data routing and isolation
- Audit logging with hash chain integrity
- Idempotency handling for financial operations
- Legal entity enforcement for financial endpoints
- Outbox pattern for reliable event publishing
- RBAC integration
- I18n client support
- Correlation ID tracking across requests

## Target Use Case

Designed for ERP microservices requiring strict tenant isolation, financial compliance, and multi-country support with localization.
