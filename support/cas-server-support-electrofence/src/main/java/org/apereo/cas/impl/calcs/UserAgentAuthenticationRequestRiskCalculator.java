package org.apereo.cas.impl.calcs;

import lombok.extern.slf4j.Slf4j;
import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.support.events.dao.CasEvent;
import org.apereo.cas.support.events.CasEventRepository;
import org.apereo.cas.util.HttpRequestUtils;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Collection;

/**
 * This is {@link UserAgentAuthenticationRequestRiskCalculator}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Slf4j
public class UserAgentAuthenticationRequestRiskCalculator extends BaseAuthenticationRequestRiskCalculator {

    
    public UserAgentAuthenticationRequestRiskCalculator(final CasEventRepository casEventRepository) {
        super(casEventRepository);
    }

    @Override
    protected BigDecimal calculateScore(final HttpServletRequest request,
                                        final Authentication authentication,
                                        final RegisteredService service,
                                        final Collection<CasEvent> events) {

        final var agent = HttpRequestUtils.getHttpServletRequestUserAgent(request);
        LOGGER.debug("Filtering authentication events for user agent [{}]", agent);
        final var count = events.stream().filter(e -> e.getAgent().equalsIgnoreCase(agent)).count();
        LOGGER.debug("Total authentication events found for [{}]: [{}]", agent, count);
        if (count == events.size()) {
            LOGGER.debug("Principal [{}] has always authenticated from [{}]", authentication.getPrincipal(), agent);
            return LOWEST_RISK_SCORE;
        }
        return getFinalAveragedScore(count, events.size());
    }
}
