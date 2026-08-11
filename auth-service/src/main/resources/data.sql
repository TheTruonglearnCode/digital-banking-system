SET search_path TO auth_schema;

INSERT INTO roles (name) VALUES ('CUSTOMER'), ('ADMIN')
ON CONFLICT (name) DO NOTHING;
