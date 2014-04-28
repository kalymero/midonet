/*
 * Copyright (c) 2014 Midokura Europe SARL, All Rights Reserved.
 */
package org.midonet.cluster.data.neutron;

import com.google.common.base.Objects;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.UUID;

public class SecurityGroupRule {

    public UUID id;

    @JsonProperty("security_group_id")
    public UUID securityGroupId;

    @JsonProperty("remote_group_id")
    public UUID remoteGroupId;

    public String direction;

    public String protocol;

    @JsonProperty("port_range_min")
    public Integer portRangeMin;

    @JsonProperty("port_range_max")
    public Integer portRangeMax;

    public String ethertype;

    @JsonProperty("remote_ip_prefix")
    public String remoteIpPrefix;

    @JsonProperty("tenant_id")
    public String tenantId;

    @Override
    public boolean equals(Object obj) {

        if (obj == this) return true;

        if (!(obj instanceof SecurityGroupRule)) return false;
        final SecurityGroupRule other = (SecurityGroupRule) obj;

        return Objects.equal(id, other.id)
                && Objects.equal(securityGroupId, other.securityGroupId)
                && Objects.equal(remoteGroupId, other.remoteGroupId)
                && Objects.equal(tenantId, other.tenantId)
                && Objects.equal(direction, other.direction)
                && Objects.equal(protocol, other.protocol)
                && Objects.equal(ethertype, other.ethertype)
                && Objects.equal(remoteIpPrefix, other.remoteIpPrefix)
                && Objects.equal(portRangeMax, other.portRangeMax)
                && Objects.equal(portRangeMin, other.portRangeMin);
    }

    @Override
    public int hashCode() {

        return Objects.hashCode(id, securityGroupId, remoteGroupId, direction,
                protocol, portRangeMax, portRangeMin, tenantId, ethertype,
                remoteIpPrefix);
    }

    @Override
    public String toString() {

        return Objects.toStringHelper(this)
                .add("id", id)
                .add("securityGroupId", securityGroupId)
                .add("remoteGroupId", remoteGroupId)
                .add("direction", direction)
                .add("protocol", protocol)
                .add("portRangeMin", portRangeMin)
                .add("portRangeMax", portRangeMax)
                .add("ethertype", ethertype)
                .add("remoteIpPrefix", remoteIpPrefix)
                .add("tenantId", tenantId).toString();
    }
}