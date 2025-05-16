create or replace function func_tx_updated() returns trigger as
$$
begin
    update "{schema}".ecm_transactions set ck_required = true where id = old.tx and old.tx <> new.tx;
    return null;
end
$$
    language plpgsql;

create or replace function func_tx_deleted() returns trigger as
$$
begin
    update "{schema}".ecm_transactions set ck_required = true where id = old.tx;
    return null;
end
$$
    language plpgsql;

drop trigger if exists tr_nodes_tx_updated on ecm_nodes;

-- create trigger tr_nodes_tx_updated
--     after update of tx on ecm_nodes
--     for each row execute function func_tx_updated();

drop trigger if exists tr_sg_tx_updated on ecm_security_groups;

-- create trigger tr_sg_tx_updated
--     after update of tx on ecm_security_groups
--     for each row execute function func_tx_updated();

drop trigger if exists tr_archived_nodes_tx_updated on ecm_archived_nodes;

-- create trigger tr_archived_nodes_tx_updated
--     after update of tx on ecm_archived_nodes
--     for each row execute function func_tx_updated();

drop trigger if exists tr_removed_nodes_tx_deleted on ecm_removed_nodes;

-- create trigger tr_removed_nodes_tx_deleted
--     after delete on ecm_removed_nodes
--     for each row execute function func_tx_deleted();
