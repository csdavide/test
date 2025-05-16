-- default
insert into ecm_tenants (tenant, schema_name) values ('default', CURRENT_SCHEMA());
insert into ecm_users (tenant, username, uuid, data) values ('default', 'admin', '00000000-0000-0000-0000-000000000000', '{"alg": "MD4", "enabled": true, "password": "209c6174da490caeb422f3fa5a7ae634"}'::jsonb);

insert into ecm_pub_keys (tenant, kid, pub_key, username, scopes)
values (
           'default', 'master',
           'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAz6qcNQsewM+UIWyyT4nJ43+oLHdzDAQPPzB/TRH5HU92uCuZwInsqSpC25C4nyx4DYucpUS4ghe/z/VeDtpjdj2ONW0J/JVk+i3VGkEI8TMQRaExZ/m5/LPPLPNhDSOKR+fHQjww3C6hk+wAMPfqYcmiGs/w5W1T/zr5S1yFKEfsJYXEARnvAA13Rn1zx0gIzCnumctUgqxRRCfju8kMIzTfIq0SWvu10R+DaODWSRz+FHVKMrAW2ccWu8xmaRrOkFY6fekR47wP6FS068kavQ0sShC3bl8tBKXeddatY6WqEGZ7aXTLtfUw7LjAcNXOKVSAZ43PyEP4FZuzkFDTawIDAQAB',
           'admin',
           'default sysadmin'
       );

