create index if not exists ix_ecm_paths_parts_array on ecm_paths using gin (path_parts);
create index if not exists ix_ecm_paths_sg_path on ecm_paths using gin (sg_path gin_trgm_ops);

create index if not exists ix_ecm_archived_nodes_aspects on ecm_archived_nodes using gin ((data->'aspects'));

create index if not exists ix_ecm_nodes_properties on ecm_nodes using gin ((data->'properties'));
create index if not exists ix_ecm_nodes_aspects on ecm_nodes using gin ((data->'aspects'));
