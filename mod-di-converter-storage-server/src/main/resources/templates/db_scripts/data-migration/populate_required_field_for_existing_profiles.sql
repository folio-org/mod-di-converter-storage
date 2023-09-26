----update existing profile's 'required' field to 'true'
--
----Holding and Item profile
-----electronicAccess
UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(
	jsonb,
	'{mappingDetails, mappingFields}',
	(
		SELECT jsonb_agg(
			CASE
				WHEN elem->>'name' = 'electronicAccess' and elem->'subfields' is not null THEN
					jsonb_set(elem, '{subfields}', (
						select jsonb_agg(
							case
								when subfields is not null then
									jsonb_set(subfields,'{fields}',
										(
											select jsonb_agg(
												case
													when subfields_fields->>'name' = 'uri' then
														jsonb_set(subfields_fields, '{required}','true')
													else
													subfields_fields
												end

											)
											from jsonb_array_elements(subfields->'fields') as subfields_fields
										)
									)
								else subfields
							end

						)
						from jsonb_array_elements(elem->'subfields') as subfields
					)
					)
				ELSE
					elem
			END)
		FROM jsonb_array_elements(jsonb-> 'mappingDetails'->'mappingFields') AS elem
	)
)
where exists ( select 1 from
	jsonb_array_elements(jsonb->'mappingDetails'->
'mappingFields') as fields,
	jsonb_array_elements(fields->'subfields') as subfields,
	jsonb_array_elements(subfields->'fields') as subfields_fields
where
	jsonb->'mappingDetails'->>'recordType' in ('HOLDINGS','ITEM')
	and fields ->>'name' = 'electronicAccess' and
	subfields is not null
	);


-----notes[](noteType, note)
UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(
	jsonb,
	'{mappingDetails, mappingFields}',
	(
		SELECT jsonb_agg(
			CASE
				WHEN elem->>'name' = 'notes' and elem->'subfields' is not null THEN
					jsonb_set(elem, '{subfields}', (
						select jsonb_agg(
							case
								when subfields is not null then
									jsonb_set(subfields,'{fields}',
										(
											select jsonb_agg(
												case
													when subfields_fields->>'name' in ('noteType', 'note') then
														jsonb_set(subfields_fields, '{required}','true')
													else
													subfields_fields
												end

											)
											from jsonb_array_elements(subfields->'fields') as subfields_fields
										)
									)
								else subfields
							end

						)
						from jsonb_array_elements(elem->'subfields') as subfields
					)
					)
				ELSE
					elem
			END)
		FROM jsonb_array_elements(jsonb-> 'mappingDetails'->'mappingFields') AS elem
	)
)
where exists ( select 1 from
	jsonb_array_elements(jsonb->'mappingDetails'->
'mappingFields') as fields,
	jsonb_array_elements(fields->'subfields') as subfields,
	jsonb_array_elements(subfields->'fields') as subfields_fields
where
	jsonb->'mappingDetails'->>'recordType' in ('HOLDINGS','ITEM')
	and fields ->>'name' = 'notes' and
	subfields is not null
	);

---circulations(noteType, note) of Item profile
UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(
	jsonb,
	'{mappingDetails, mappingFields}',
	(
		SELECT jsonb_agg(
			CASE
				WHEN elem->>'name' = 'circulationNotes' and elem->'subfields' is not null THEN
					jsonb_set(elem, '{subfields}', (
						select jsonb_agg(
							case
								when subfields is not null then
									jsonb_set(subfields,'{fields}',
										(
											select jsonb_agg(
												case
													when subfields_fields->>'name' in ('noteType', 'note') then
														jsonb_set(subfields_fields, '{required}','true')
													else
													subfields_fields
												end

											)
											from jsonb_array_elements(subfields->'fields') as subfields_fields
										)
									)
								else subfields
							end

						)
						from jsonb_array_elements(elem->'subfields') as subfields
					)
					)
				ELSE
					elem
			END)
		FROM jsonb_array_elements(jsonb-> 'mappingDetails'->'mappingFields') AS elem
	)
)
where exists ( select 1 from
	jsonb_array_elements(jsonb->'mappingDetails'->
'mappingFields') as fields,
	jsonb_array_elements(fields->'subfields') as subfields,
	jsonb_array_elements(subfields->'fields') as subfields_fields
where
	jsonb->'mappingDetails'->>'recordType' = 'ITEM'
	and fields ->>'name' = 'circulationNotes' and
	subfields is not null
	);
