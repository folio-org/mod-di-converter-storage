----update existing profile's 'required' field to 'true'
--
----Holding AND Item profile
-----electronicAccess
UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(
	jsonb,
	'{mappingDetails, mappingFields}',
	(
		SELECT jsonb_agg(
			CASE
				WHEN elem->>'name' = 'electronicAccess' AND elem->'subfields' IS NOT NULL THEN
					jsonb_set(elem, '{subfields}', (
						SELECT jsonb_agg(
							CASE
								WHEN subfields IS NOT NULL THEN
									jsonb_set(subfields,'{fields}',
										(
											SELECT jsonb_agg(
												CASE
													WHEN subfields_fields->>'name' = 'uri' THEN
														jsonb_set(subfields_fields, '{required}','true')
													ELSE
													subfields_fields
												END

											)
											FROM jsonb_array_elements(subfields->'fields') AS subfields_fields
										)
									)
								ELSE subfields
							END

						)
						FROM jsonb_array_elements(elem->'subfields') AS subfields
					)
					)
				ELSE
					elem
			END)
		FROM jsonb_array_elements(jsonb-> 'mappingDetails'->'mappingFields') AS elem
	)
)
WHERE EXISTS ( SELECT 1 FROM
	jsonb_array_elements(jsonb->'mappingDetails'->
'mappingFields') AS fields,
	jsonb_array_elements(fields->'subfields') AS subfields,
	jsonb_array_elements(subfields->'fields') AS subfields_fields
WHERE
	jsonb->'mappingDetails'->>'recordType' IN ('HOLDINGS','ITEM')
	AND fields ->>'name' = 'electronicAccess' AND
	subfields IS NOT NULL
	);


-----notes[](noteType, note)
UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(
	jsonb,
	'{mappingDetails, mappingFields}',
	(
		SELECT jsonb_agg(
			CASE
				WHEN elem->>'name' = 'notes' AND elem->'subfields' IS NOT NULL THEN
					jsonb_set(elem, '{subfields}', (
						SELECT jsonb_agg(
							CASE
								WHEN subfields IS NOT NULL THEN
									jsonb_set(subfields,'{fields}',
										(
											SELECT jsonb_agg(
												CASE
													WHEN subfields_fields->>'name' IN ('noteType', 'note') THEN
														jsonb_set(subfields_fields, '{required}','true')
													ELSE
													subfields_fields
												END

											)
											FROM jsonb_array_elements(subfields->'fields') AS subfields_fields
										)
									)
								ELSE subfields
							END

						)
						FROM jsonb_array_elements(elem->'subfields') AS subfields
					)
					)
				ELSE
					elem
			END)
		FROM jsonb_array_elements(jsonb-> 'mappingDetails'->'mappingFields') AS elem
	)
)
WHERE EXISTS ( SELECT 1 FROM
	jsonb_array_elements(jsonb->'mappingDetails'->
'mappingFields') AS fields,
	jsonb_array_elements(fields->'subfields') AS subfields,
	jsonb_array_elements(subfields->'fields') AS subfields_fields
WHERE
	jsonb->'mappingDetails'->>'recordType' IN ('HOLDINGS','ITEM')
	AND fields ->>'name' = 'notes' AND
	subfields IS NOT NULL
	);

---circulations(noteType, note) of Item profile
UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(
	jsonb,
	'{mappingDetails, mappingFields}',
	(
		SELECT jsonb_agg(
			CASE
				WHEN elem->>'name' = 'circulationNotes' AND elem->'subfields' IS NOT NULL THEN
					jsonb_set(elem, '{subfields}', (
						SELECT jsonb_agg(
							CASE
								WHEN subfields IS NOT NULL THEN
									jsonb_set(subfields,'{fields}',
										(
											SELECT jsonb_agg(
												CASE
													WHEN subfields_fields->>'name' IN ('noteType', 'note') THEN
														jsonb_set(subfields_fields, '{required}','true')
													ELSE
													subfields_fields
												END

											)
											FROM jsonb_array_elements(subfields->'fields') AS subfields_fields
										)
									)
								ELSE subfields
							END

						)
						FROM jsonb_array_elements(elem->'subfields') AS subfields
					)
					)
				ELSE
					elem
			END)
		FROM jsonb_array_elements(jsonb-> 'mappingDetails'->'mappingFields') AS elem
	)
)
WHERE EXISTS ( SELECT 1 FROM
	jsonb_array_elements(jsonb->'mappingDetails'->
'mappingFields') AS fields,
	jsonb_array_elements(fields->'subfields') AS subfields,
	jsonb_array_elements(subfields->'fields') AS subfields_fields
WHERE
	jsonb->'mappingDetails'->>'recordType' = 'ITEM'
	AND fields ->>'name' = 'circulationNotes' AND
	subfields IS NOT NULL
	);
