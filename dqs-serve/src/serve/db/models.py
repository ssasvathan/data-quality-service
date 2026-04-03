"""SQLAlchemy ORM models and shared constants for dqs-serve.

Temporal pattern:
    All DQS tables use create_date + expiry_date (TIMESTAMP) columns.
    Active records carry expiry_date = EXPIRY_SENTINEL ('9999-12-31 23:59:59').
    Soft-delete = update expiry_date to current timestamp, then insert new active row.
    NEVER hardcode '9999-12-31 23:59:59' inline — always reference EXPIRY_SENTINEL.
    See project-context.md § Temporal Data Pattern for the full cross-runtime rule.
"""
from sqlalchemy.orm import DeclarativeBase

# Sentinel timestamp marking an "active" (non-expired) record in the temporal pattern.
# All tables that participate in the temporal pattern use this value as the expiry_date
# DEFAULT in DDL and as the filter value when inserting or querying active records.
# Reference: project-context.md § Temporal Data Pattern (ALL COMPONENTS)
EXPIRY_SENTINEL: str = "9999-12-31 23:59:59"


class Base(DeclarativeBase):
    pass
