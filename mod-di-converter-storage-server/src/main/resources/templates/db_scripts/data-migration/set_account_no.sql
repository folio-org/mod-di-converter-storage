-- mapping_profiles:
-- change values: set accountNo for mapping profiles with record type INVOICE
UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_insert(jsonb, '{mappingDetails,mappingFields,19}', '{"name": "accountNo", "enabled": "false", "path": "invoice.accountNo", "value": "", "subfields": []}')
WHERE jsonb -> 'mappingDetails' ->> 'recordType' = 'INVOICE';
