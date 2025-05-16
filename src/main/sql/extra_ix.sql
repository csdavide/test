-- solo per management
create index if not exists ix_ecm_transactions_uuid on ecm_transactions using hash (uuid);

-- necessari per utilizzare i SG "unmanaged"
create unique index if not exists ix_ecm_security_groups_uuid_uq on ecm_security_groups using btree (tenant,uuid);
create unique index if not exists ix_ecm_security_groups_name_uq on ecm_security_groups using btree (tenant,"name");

-- necessario per funzioni aggiuntive
create unique index if not exists ix_ecm_ecm_nodes_code_uq on ecm_nodes using btree (tenant, type_name, code);
