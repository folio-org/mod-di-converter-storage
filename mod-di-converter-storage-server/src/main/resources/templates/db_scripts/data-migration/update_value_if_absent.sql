UPDATE ${myuniversity}_${mymodule}.match_profiles
SET jsonb = jsonb_set(
	jsonb,
	'{matchDetails}',
	(
		SELECT jsonb_agg(
			CASE
					WHEN matchME->>'incomingMatchExpression' IS NOT NULL THEN
					jsonb_set(matchME,'{incomingMatchExpression, fields}',
					(
					SELECT jsonb_agg(
						CASE
							WHEN fields_incoming->>'value' IS NULL THEN
							jsonb_set(fields_incoming, '{value}','""')
							ELSE
							fields_incoming
						END
					)
					FROM jsonb_array_elements(matchme->'incomingMatchExpression'->'fields') AS fields_incoming
					)
					)
			ELSE matchME
			END)
		FROM jsonb_array_elements(jsonb-> 'matchDetails') AS matchME
	)
)
WHERE EXISTS ( SELECT 1 FROM
	jsonb_array_elements(jsonb->'matchDetails') AS matchDetails,
jsonb_array_elements(matchDetails->'incomingMatchExpression'->'fields') AS fields_incoming
WHERE fields_incoming->>'value' IS NULL
);
