-- FUNCS SCRIPT

create or replace function func_path_parts(p text) returns int[] language plpgsql as
$$
declare
    a text[];
    n integer;
    result int[];
begin
    a := array_remove(string_to_array(p,':'), '');
    n := cardinality(a);
    for i in 1..n loop
            result := array_append(result, a[i]::int);
        end loop;
    return result;
end
$$;

create or replace function func_path_combine(parent_file_path text, file_path text, node_path text, node_id text, association_name text)
    returns text
    language plpgsql
as
$$
declare
    node_path_elements text[];
    file_path_elements text[];
    k int;
    n int;
    x text[];
begin
    node_path_elements := array_remove(string_to_array(node_path, ':'), '');
    file_path_elements := array_remove(string_to_array(file_path,'/'), '');
    k := array_position(node_path_elements, node_id);
    n := k + cardinality(file_path_elements) - cardinality(node_path_elements) + 1;
    for i in reverse cardinality(file_path_elements) .. n loop
            x := array_prepend(file_path_elements[i], x);
        end loop;
    x := array_prepend(association_name, x);
    return parent_file_path || array_to_string(x, '/') || '/';
end
$$;

create or replace function func_path_rename(name text, file_path text, node_path text, node_id text)
    returns text
    language plpgsql
as
$$
declare
    node_path_elements text[];
    file_path_elements text[];
    pos int;
begin
    node_path_elements := array_remove(string_to_array(node_path, ':'), '');
    file_path_elements := array_remove(string_to_array(file_path,'/'), '');
    pos := array_position(node_path_elements, node_id) + cardinality(file_path_elements) - cardinality(node_path_elements);
    file_path_elements[pos] := name;
    return  '/' || array_to_string(file_path_elements, '/') || '/';
end
$$;

create or replace function func_path_rename(name text, file_path text, path_parts integer[], node_id integer)
    returns text
    language plpgsql
as
$$
declare
    file_path_elements text[];
    pos int;
begin
    file_path_elements := array_remove(string_to_array(file_path,'/'), '');
    pos := array_position(path_parts, node_id) + cardinality(file_path_elements) - cardinality(path_parts);
    file_path_elements[pos] := name;
    return  '/' || array_to_string(file_path_elements, '/') || '/';
end
$$;
