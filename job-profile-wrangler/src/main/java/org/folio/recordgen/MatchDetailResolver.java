package org.folio.recordgen;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.processing.value.DateValue;
import org.folio.processing.value.MissingValue;
import org.folio.processing.value.StringValue;
import org.folio.processing.value.Value;
import org.folio.rest.jaxrs.model.Field;
import org.folio.rest.jaxrs.model.MarcField;
import org.folio.rest.jaxrs.model.MarcSubfield;
import org.folio.rest.jaxrs.model.MatchDetail;
import org.folio.rest.jaxrs.model.MatchExpression;
import org.folio.rest.jaxrs.model.StaticValueDetails;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.nonNull;
import static org.folio.rest.jaxrs.model.MatchExpression.DataValueType.STATIC_VALUE;
import static org.folio.rest.jaxrs.model.MatchExpression.DataValueType.VALUE_FROM_RECORD;

/**
 * Resolves match details based on incoming and existing match expressions.
 * TODO: consider match criterion
 */
public class MatchDetailResolver {

    public static final String FIELD_PROFILE_LABEL = "field";
    public static final String IND_1_PROFILE_LABEL = "indicator1";
    public static final String IND_2_PROFILE_LABEL = "indicator2";
    public static final String SUBFIELD_PROFILE_LABEL = "recordSubfield";

    /**
     * Resolves a match detail based on the incoming and existing match expressions.
     *
     * @param matchDetail The match detail to resolve.
     * @param isNonMatch  Indicates whether it is a non-match.
     * @return The resolved match.
     */
    public ResolvedMatch resolve(MatchDetail matchDetail, boolean isNonMatch) {
        MatchExpression incomingMatchExpression = matchDetail.getIncomingMatchExpression();
        MatchExpression existingMatchExpression = matchDetail.getExistingMatchExpression();
        var incomingPair = resolveMatch(incomingMatchExpression);
        var existingPair = resolveMatch(existingMatchExpression);
        ResolvedMatch.ResolvedMatchBuilder builder = ResolvedMatch.builder();
        incomingPair.ifPresent(pair ->
                builder
                        .incomingDataValueType(pair.getKey())
                        .incoming(pair.getValue()));
        existingPair.ifPresent(pair ->
                builder
                        .existingDataValueType(pair.getKey())
                        .existing(pair.getValue()));
        builder.isNonMatch(isNonMatch);
        return builder.build();
    }

    /**
     * Resolves a match expression.
     *
     * @param matchExpression The match expression to resolve.
     * @return An optional pair containing the data value type and the resolved value.
     */
    private Optional<Pair<MatchExpression.DataValueType, Object>> resolveMatch(MatchExpression matchExpression) {
        if (matchExpression.getDataValueType() == STATIC_VALUE && nonNull(matchExpression.getStaticValueDetails())) {
            StaticValueDetails staticValueDetails = matchExpression.getStaticValueDetails();
            return Optional.of(Pair.of(STATIC_VALUE, switch (staticValueDetails.getStaticValueType()) {
                case TEXT -> obtainStringValue(staticValueDetails.getText());
                case NUMBER -> obtainStringValue(staticValueDetails.getNumber());
                case EXACT_DATE ->
                        obtainDateValue(staticValueDetails.getExactDate(), staticValueDetails.getExactDate());
                case DATE_RANGE -> obtainDateValue(staticValueDetails.getFromDate(), staticValueDetails.getToDate());
            }));
        } else if (matchExpression.getDataValueType() == VALUE_FROM_RECORD) {
            List<Field> fields = matchExpression.getFields();
            if (fields.size() == 1) {
                return Optional.of(Pair.of(VALUE_FROM_RECORD, fields.get(0)));
            }
            MarcField marcField = fields.stream()
                    .reduce(new MarcField(), (result, field) -> {
                        if (FIELD_PROFILE_LABEL.equals(field.getLabel())) {
                            result = result.withField(field.getValue());
                        } else if (IND_1_PROFILE_LABEL.equals(field.getLabel())) {
                            result = result.withIndicator1(field.getValue());
                        } else if (IND_2_PROFILE_LABEL.equals(field.getLabel())) {
                            result = result.withIndicator2(field.getValue());
                        } else if (SUBFIELD_PROFILE_LABEL.equals(field.getLabel())) {
                            result = result.withSubfields(List.of(new MarcSubfield().withSubfield(field.getValue())));
                        }
                        return result;
                    }, (result1, result2) -> result1);
            return Optional.of(Pair.of(VALUE_FROM_RECORD, marcField));
        }
        return Optional.empty();
    }

    /**
     * Obtains a string value from the given input.
     *
     * @param value The input value.
     * @return The string value or a missing value if the input is null.
     */
    private Value obtainStringValue(String value) {
        return nonNull(value) ? StringValue.of(value) : MissingValue.getInstance();
    }

    /**
     * Obtains a date value from the given from and to dates.
     *
     * @param from The from date.
     * @param to   The to date.
     * @return The date value or a missing value if either from or to date is null.
     */
    private Value obtainDateValue(Date from, Date to) {
        return nonNull(from) && nonNull(to) ? DateValue.of(from, to) : MissingValue.getInstance();
    }
}
