alter table ecm_nodes add constraint ecm_nodes_tx_fk foreign key (tx) references ecm_transactions(id);
alter table ecm_nodes add constraint ecm_nodes_sg_fk foreign key (sg_id) references ecm_security_groups(id);

alter table ecm_associations add constraint ecm_associations_parent_fk foreign key (parent_id) references ecm_nodes(id);
alter table ecm_associations add constraint ecm_associations_child_fk foreign key (child_id) references ecm_nodes(id);

alter table ecm_archived_nodes add constraint ecm_archived_nodes_tx_fk foreign key (tx) references ecm_transactions(id);
alter table ecm_archived_nodes add constraint ecm_archived_nodes_sg_fk foreign key (sg_id) references ecm_security_groups(id);

alter table ecm_security_groups add constraint ecm_sg_tx_fk foreign key (tx) references ecm_transactions(id);

alter table ecm_access_rules add constraint ecm_access_rules_sg_fk foreign key (sg_id) references ecm_security_groups(id);
