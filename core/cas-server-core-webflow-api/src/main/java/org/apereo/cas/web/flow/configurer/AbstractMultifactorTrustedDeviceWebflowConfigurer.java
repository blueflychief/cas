package org.apereo.cas.web.flow.configurer;

import lombok.extern.slf4j.Slf4j;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.web.flow.CasWebflowConstants;
import org.springframework.context.ApplicationContext;
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry;
import org.springframework.webflow.engine.ActionState;
import org.springframework.webflow.engine.Flow;
import org.springframework.webflow.engine.Transition;
import org.springframework.webflow.engine.builder.support.FlowBuilderServices;
import org.springframework.webflow.engine.support.DefaultTargetStateResolver;

import java.util.Arrays;

/**
 * This is {@link AbstractMultifactorTrustedDeviceWebflowConfigurer}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Slf4j
public abstract class AbstractMultifactorTrustedDeviceWebflowConfigurer extends AbstractCasMultifactorWebflowConfigurer {
    /**
     * Trusted authentication scope attribute.
     **/
    public static final String MFA_TRUSTED_AUTHN_SCOPE_ATTR = "mfaTrustedAuthentication";
    
    private static final String MFA_VERIFY_TRUST_ACTION_BEAN_ID = "mfaVerifyTrustAction";
    private static final String MFA_SET_TRUST_ACTION_BEAN_ID = "mfaSetTrustAction";
    
    private final boolean enableDeviceRegistration;

    public AbstractMultifactorTrustedDeviceWebflowConfigurer(final FlowBuilderServices flowBuilderServices,
                                                             final FlowDefinitionRegistry loginFlowDefinitionRegistry,
                                                             final boolean enableDeviceRegistration, final ApplicationContext applicationContext,
                                                             final CasConfigurationProperties casProperties) {
        super(flowBuilderServices, loginFlowDefinitionRegistry, applicationContext, casProperties);
        this.enableDeviceRegistration = enableDeviceRegistration;
    }

    /**
     * Register multifactor trusted authentication into webflow.
     *
     * @param flowDefinitionRegistry the flow definition registry
     */
    protected void registerMultifactorTrustedAuthentication(final FlowDefinitionRegistry flowDefinitionRegistry) {
        validateFlowDefinitionConfiguration(flowDefinitionRegistry);

        LOGGER.debug("Flow definitions found in the registry are [{}]", (Object[]) flowDefinitionRegistry.getFlowDefinitionIds());
        final var flowId = Arrays.stream(flowDefinitionRegistry.getFlowDefinitionIds()).findFirst().get();
        LOGGER.debug("Processing flow definition [{}]", flowId);

        final var flow = (Flow) flowDefinitionRegistry.getFlowDefinition(flowId);

        // Set the verify action
        final var state = getState(flow, CasWebflowConstants.STATE_ID_INIT_LOGIN_FORM, ActionState.class);
        final var transition = (Transition) state.getTransition(CasWebflowConstants.TRANSITION_ID_SUCCESS);
        final var targetStateId = transition.getTargetStateId();
        transition.setTargetStateResolver(new DefaultTargetStateResolver(CasWebflowConstants.STATE_ID_VERIFY_TRUSTED_DEVICE));
        final var verifyAction = createActionState(flow,
                CasWebflowConstants.STATE_ID_VERIFY_TRUSTED_DEVICE,
                createEvaluateAction(MFA_VERIFY_TRUST_ACTION_BEAN_ID));

        // handle device registration
        if (enableDeviceRegistration) {
            createTransitionForState(verifyAction, CasWebflowConstants.TRANSITION_ID_YES, CasWebflowConstants.STATE_ID_FINISH_MFA_TRUSTED_AUTH);
        } else {
            createTransitionForState(verifyAction, CasWebflowConstants.TRANSITION_ID_YES, CasWebflowConstants.STATE_ID_REAL_SUBMIT);
        }
        createTransitionForState(verifyAction, CasWebflowConstants.TRANSITION_ID_NO, targetStateId);

        createDecisionState(flow, CasWebflowConstants.DECISION_STATE_REQUIRE_REGISTRATION,
                isDeviceRegistrationRequired(),
                CasWebflowConstants.VIEW_ID_REGISTER_DEVICE, CasWebflowConstants.STATE_ID_REAL_SUBMIT);

        final var submit = getState(flow, CasWebflowConstants.STATE_ID_REAL_SUBMIT, ActionState.class);
        final var success = (Transition) submit.getTransition(CasWebflowConstants.TRANSITION_ID_SUCCESS);
        if (enableDeviceRegistration) {
            success.setTargetStateResolver(new DefaultTargetStateResolver(CasWebflowConstants.VIEW_ID_REGISTER_DEVICE));
        } else {
            success.setTargetStateResolver(new DefaultTargetStateResolver(CasWebflowConstants.STATE_ID_REGISTER_TRUSTED_DEVICE));
        }
        final var viewRegister = createViewState(flow, CasWebflowConstants.VIEW_ID_REGISTER_DEVICE, "casMfaRegisterDeviceView");
        final var viewRegisterTransition = createTransition(CasWebflowConstants.TRANSITION_ID_SUBMIT,
                CasWebflowConstants.STATE_ID_REGISTER_TRUSTED_DEVICE);
        viewRegister.getTransitionSet().add(viewRegisterTransition);

        final var registerAction = createActionState(flow,
                CasWebflowConstants.STATE_ID_REGISTER_TRUSTED_DEVICE, createEvaluateAction(MFA_SET_TRUST_ACTION_BEAN_ID));
        createStateDefaultTransition(registerAction, CasWebflowConstants.STATE_ID_SUCCESS);

        if (submit.getActionList().size() == 0) {
            throw new IllegalArgumentException("There are no actions defined for the final submission event of " + flowId);
        }
        final var act = submit.getActionList().iterator().next();
        final var finishMfaTrustedAuth = createActionState(flow, CasWebflowConstants.STATE_ID_FINISH_MFA_TRUSTED_AUTH, act);
        final var finishedTransition = createTransition(CasWebflowConstants.TRANSITION_ID_SUCCESS, CasWebflowConstants.STATE_ID_SUCCESS);
        finishMfaTrustedAuth.getTransitionSet().add(finishedTransition);
        createStateDefaultTransition(finishMfaTrustedAuth, CasWebflowConstants.STATE_ID_SUCCESS);
    }

    private void validateFlowDefinitionConfiguration(final FlowDefinitionRegistry flowDefinitionRegistry) {
        if (flowDefinitionRegistry.getFlowDefinitionCount() <= 0) {
            throw new IllegalArgumentException("Flow definition registry has no flow definitions");
        }

        final var msg = "CAS application context cannot find bean [%s]. "
                + "This typically indicates that configuration is attempting to activate trusted-devices functionality for "
                + "multifactor authentication, yet the configuration modules that auto-configure the webflow are absent "
                + "from the CAS application runtime. If you have no need for trusted-devices functionality and wish to let the "
                + "multifactor authentication provider (and not CAS) remember and record trusted devices for you, you need to "
                + "turn this behavior off.";

        if (!applicationContext.containsBean(MFA_SET_TRUST_ACTION_BEAN_ID)) {
            throw new IllegalArgumentException(String.format(msg, MFA_SET_TRUST_ACTION_BEAN_ID));
        }

        if (!applicationContext.containsBean(MFA_VERIFY_TRUST_ACTION_BEAN_ID)) {
            throw new IllegalArgumentException(String.format(msg, MFA_VERIFY_TRUST_ACTION_BEAN_ID));
        }
    }
    
    private static String isDeviceRegistrationRequired() {
        return "flashScope.".concat(MFA_TRUSTED_AUTHN_SCOPE_ATTR).concat("== null");
    }
}
