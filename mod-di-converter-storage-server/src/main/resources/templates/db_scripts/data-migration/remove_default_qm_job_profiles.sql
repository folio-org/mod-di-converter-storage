-- Removes all default "quickMARC" (QM) job profile trees and their related
-- action/match/mapping profiles, wrappers and associations:
--   * Default - Create authorities (quickMARC - Default Create authority)
--   * Default - Update authorities (quickMARC - Default Update authority)
--   * quickMARC Derive - Create Holdings and SRS MARC Holdings
--   * quickMARC - Default Update holdings
--   * quickMARC - Derive a new SRS MARC Bib and Instance
--   * quickMARC - Default Update instance

DO
$$
DECLARE
    qm_job_profile_ids uuid[] := ARRAY[
        '6eefa4c6-bbf7-4845-ad82-de7fc4abd0e3',
        'c7fcbc40-c4c0-411d-b569-1fc6bc142a92',
        'fa0262c7-5816-48d0-b9b3-7b7a862a5bc7',
        '6cb347c6-c0b0-4363-89fc-32cedede87ba',
        '6409dcff-71fa-433a-bc6a-e70ad38a9604',
        'cf6f2718-5aa5-482a-bba5-5bc9b75614da'
    ];
    qm_action_profile_ids uuid[] := ARRAY[
        '7915c72e-c7af-4962-969d-403c7238b051',
        'f0f788c8-2e65-4e3a-9247-e9444eeb7d70',
        'f5feddba-f892-4fad-b702-e4e77f04f9a3',
        '7e24a466-349b-451d-a18e-38fb21d71b38',
        'f8e58651-f651-485d-aead-d2fa8700e2d1',
        'c2e2d482-9486-476e-a28c-8f1e303cbe1a'
    ];
    qm_match_profile_ids uuid[] := ARRAY[
        'aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf',
        '2a599369-817f-4fe8-bae2-f3e3987990fe',
        '91cec42a-260d-4a8c-a9fb-90d9435ca2f4'
    ];
    qm_mapping_profile_ids uuid[] := ARRAY[
        '6a0ec1de-68eb-4833-bdbf-0741db85c314',
        '041f8ff9-9d17-4436-b305-1033e0879501',
        'e0fbaad5-10c0-40d5-9228-498b351dbbaa',
        'b8a9ca7d-4a33-44d3-86e1-f7c6cb7b265f',
        '991c0300-44a6-47e3-8ea2-b01bb56a38cc',
        '39b265e1-c963-4e5f-859d-6e8c327a265c'
    ];
BEGIN
    -- profile_wrappers -> profile_associations are removed automatically via ON DELETE CASCADE
    DELETE FROM ${myuniversity}_${mymodule}.profile_wrappers
    WHERE job_profile_id = ANY (qm_job_profile_ids)
       OR action_profile_id = ANY (qm_action_profile_ids)
       OR match_profile_id = ANY (qm_match_profile_ids)
       OR mapping_profile_id = ANY (qm_mapping_profile_ids);

    DELETE FROM ${myuniversity}_${mymodule}.job_profiles
    WHERE id = ANY (qm_job_profile_ids);

    DELETE FROM ${myuniversity}_${mymodule}.action_profiles
    WHERE id = ANY (qm_action_profile_ids);

    DELETE FROM ${myuniversity}_${mymodule}.match_profiles
    WHERE id = ANY (qm_match_profile_ids);

    DELETE FROM ${myuniversity}_${mymodule}.mapping_profiles
    WHERE id = ANY (qm_mapping_profile_ids);
END
$$;
