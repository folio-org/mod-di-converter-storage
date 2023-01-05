-- rename schema to mod-di-converter-storage
ALTER SCHEMA ${myuniversity}_mod_data_import_converter_storage
RENAME TO ${myuniversity}_${mymodule};

-- rename role to mod_di_converter_storage
ALTER ROLE ${myuniversity}_mod_data_import_converter_storage
RENAME TO ${myuniversity}_${mymodule};
