create unique index if not exists ecm_users_username_uq on ecm_users (tenant, lower(username));

create index if not exists ix_ecm_transactions_create_at on ecm_transactions (created_at);
create index if not exists ix_ecm_transactions_indexed on ecm_transactions (indexed_at) where indexed_at is null;
create index if not exists ix_ecm_transactions_ck_required on ecm_transactions (ck_required) where ck_required = true;

create index if not exists ix_ecm_security_groups_tx on ecm_security_groups using btree (tx);
create index if not exists ix_ecm_security_groups_src on ecm_security_groups using btree (sg_src);

create index if not exists ix_ecm_access_rules_sg on ecm_access_rules using btree (sg_id);
create index if not exists ix_ecm_access_rules_authority on ecm_access_rules using btree (authority);

create index if not exists ix_ecm_nodes_tx on ecm_nodes using btree (tx);
create index if not exists ix_ecm_nodes_uuid_hash on ecm_nodes using hash (uuid);

create index if not exists ix_ecm_associations_parent on ecm_associations using btree (parent_id, name);
create index if not exists ix_ecm_associations_child on ecm_associations using btree (child_id);

create index if not exists ix_ecm_paths_node_id on ecm_paths using btree (node_id);
create index if not exists ix_ecm_paths_lev on ecm_paths using btree (lev);
create unique index if not exists ix_ecm_paths_node_path_uq on ecm_paths using btree (node_path);
create index if not exists ix_ecm_paths_tx on ecm_paths using btree (tx);
create index if not exists ix_ecm_paths_parent_id on ecm_paths using btree (parent_id);
create index if not exists ix_ecm_paths_file_path_hash on ecm_paths using hash (file_path);

create index if not exists ix_ecm_archived_nodes_tx on ecm_archived_nodes using btree (tx);
create index if not exists ix_ecm_archived_nodes_uuid_hash on ecm_archived_nodes using hash (uuid);

create index if not exists ix_ecm_archived_associations_child on ecm_archived_associations using btree (child_id);

create index if not exists ix_ecm_removed_nodes_tx on ecm_removed_nodes using btree (tx);
