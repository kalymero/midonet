/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.filter.auth;

import com.google.inject.Inject;
import com.midokura.midonet.api.auth.AuthAction;
import com.midokura.midonet.api.auth.Authorizer;
import com.midokura.midolman.state.StateAccessException;
import com.midokura.midonet.cluster.DataClient;
import com.midokura.midonet.cluster.data.Chain;
import com.midokura.midonet.cluster.data.Rule;
import com.midokura.midonet.cluster.data.rules.JumpRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.SecurityContext;
import java.util.UUID;

/**
 * Authorizer for rule
 */
public class RuleAuthorizer extends Authorizer<UUID> {

    private final static Logger log = LoggerFactory
            .getLogger(RuleAuthorizer.class);

    private final DataClient dataClient;

    @Inject
    public RuleAuthorizer(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public boolean authorize(SecurityContext context, AuthAction action,
                             UUID id) throws StateAccessException {
        log.debug("authorize entered: id=" + id + ",action=" + action);

        if (isAdmin(context)) {
            return true;
        }

        boolean authorized = false;
        Rule rule = dataClient.rulesGet(id);
        if (rule == null) {
            log.warn("Attempted to authorize a non-existent resource: {}", id);
            return false;
        }

        Chain chain = dataClient.chainsGet(rule.getChainId());
        String tenantId = chain.getProperty(Chain.Property.tenant_id);
        if (tenantId == null) {
            log.warn("Cannot authorize rule {} because chain {} is missing " +
                    "tenant data", rule.getId(), chain.getId());
            return false;
        }

        if (!isOwner(context, tenantId)) {
            return false;
        }

        // Check the destination jump Chain ID if it's a jump rule
        if (rule instanceof JumpRule) {
            JumpRule typedRule = (JumpRule) rule;
            if (typedRule.getJumpToChainId() == null) {
                return true;
            }

            Chain targetChain =  dataClient.chainsGet(
                        typedRule.getJumpToChainId());

            if (targetChain == null) {
                log.warn("Attempted to jump to a non-existent resource: {}",
                    typedRule.getId());
                return false;
            }

            tenantId = targetChain.getProperty(Chain.Property.tenant_id);
            if (tenantId == null) {
                log.warn("Cannot authorize rule {} because jump target chain " +
                        "{} is missing tenant data", typedRule.getId(),
                        targetChain.getId());
                return false;
            }

            // Make sure the target chain is owned by the same tenant
            if (!isOwner(context, tenantId)) {
                return false;
            }
        }

        return true;
    }
}