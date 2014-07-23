/*
 * Copyright (c) 2014 Midokura SARL, All Rights Reserved.
 */
package org.midonet.cluster.data.neutron;

import org.midonet.cluster.data.Rule;
import org.midonet.cluster.data.l4lb.*;
import org.midonet.cluster.data.l4lb.HealthMonitor;
import org.midonet.cluster.data.l4lb.Pool;
import org.midonet.cluster.data.l4lb.VIP;
import org.midonet.cluster.data.neutron.loadbalancer.*;
import org.midonet.midolman.serialization.SerializationException;
import org.midonet.midolman.state.InvalidStateOperationException;
import org.midonet.midolman.state.PoolHealthMonitorMappingStatus;
import org.midonet.midolman.state.StateAccessException;
import org.midonet.midolman.state.l4lb.LBStatus;
import org.midonet.midolman.state.l4lb.MappingStatusException;
import org.midonet.midolman.state.l4lb.MappingViolationException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import java.util.List;
import java.util.UUID;

public interface LBaaSApi {

    /* load balancers related methods */
    boolean loadBalancerExists(UUID id)
        throws StateAccessException;

    @CheckForNull
    LoadBalancer loadBalancerGet(UUID id)
        throws StateAccessException, SerializationException;

    void loadBalancerDelete(UUID id)
        throws StateAccessException, SerializationException;

    UUID loadBalancerCreate(@Nonnull LoadBalancer loadBalancer)
        throws StateAccessException, SerializationException,
               InvalidStateOperationException;

    void loadBalancerUpdate(@Nonnull LoadBalancer loadBalancer)
        throws StateAccessException, SerializationException,
               InvalidStateOperationException;

    List<LoadBalancer> loadBalancersGetAll()
        throws StateAccessException, SerializationException;

    List<Pool> loadBalancerGetPools(UUID id)
        throws StateAccessException, SerializationException;

    List<VIP> loadBalancerGetVips(UUID id)
        throws StateAccessException, SerializationException;

    /* health monitors related methods */
    boolean healthMonitorExists(UUID id)
        throws StateAccessException;

    @CheckForNull
    HealthMonitor healthMonitorGet(UUID id)
        throws StateAccessException, SerializationException;

    void healthMonitorDelete(UUID id)
        throws MappingStatusException, StateAccessException,
               SerializationException;

    UUID healthMonitorCreate(@Nonnull HealthMonitor healthMonitor)
        throws StateAccessException, SerializationException;

    void healthMonitorUpdate(@Nonnull HealthMonitor healthMonitor)
        throws MappingStatusException, StateAccessException,
               SerializationException;

    List<HealthMonitor> healthMonitorsGetAll() throws StateAccessException,
                                                      SerializationException;

    List<Pool> healthMonitorGetPools(UUID id)
        throws StateAccessException, SerializationException;

    /* pool member related methods */
    boolean poolMemberExists(UUID id)
        throws StateAccessException;

    @CheckForNull
    PoolMember poolMemberGet(UUID id)
        throws StateAccessException, SerializationException;

    void poolMemberDelete(UUID id)
        throws MappingStatusException, StateAccessException,
               SerializationException;

    UUID poolMemberCreate(@Nonnull PoolMember poolMember)
        throws MappingStatusException, StateAccessException,
               SerializationException;

    void poolMemberUpdate(@Nonnull PoolMember poolMember)
        throws MappingStatusException, StateAccessException,
               SerializationException;

    void poolMemberUpdateStatus(UUID poolMemberId, LBStatus status)
        throws StateAccessException, SerializationException;

    List<PoolMember> poolMembersGetAll() throws StateAccessException,
                                                SerializationException;

    /* pool related methods */
    boolean poolExists(UUID id)
        throws StateAccessException;

    @CheckForNull
    Pool poolGet(UUID id)
        throws StateAccessException, SerializationException;

    void poolDelete(UUID id)
        throws MappingStatusException, StateAccessException,
               SerializationException;

    UUID poolCreate(@Nonnull Pool pool)
        throws MappingStatusException, StateAccessException,
               SerializationException;

    void poolUpdate(@Nonnull Pool pool)
        throws MappingStatusException, MappingViolationException,
               SerializationException, StateAccessException;

    List<Pool> poolsGetAll() throws StateAccessException,
                                    SerializationException;

    List<PoolMember> poolGetMembers(UUID id)
        throws StateAccessException, SerializationException;

    List<VIP> poolGetVips(UUID id)
        throws StateAccessException, SerializationException;

    void poolSetMapStatus(UUID id, PoolHealthMonitorMappingStatus status)
        throws StateAccessException, SerializationException;

    /* VIP related methods */
    boolean vipExists(UUID id)
        throws StateAccessException;

    @CheckForNull
    VIP vipGet(UUID id)
        throws StateAccessException, SerializationException;

    void vipDelete(UUID id)
        throws MappingStatusException, StateAccessException,
               SerializationException;

    UUID vipCreate(@Nonnull VIP vip)
        throws MappingStatusException, StateAccessException,
               SerializationException;

    void vipUpdate(@Nonnull VIP vip)
        throws MappingStatusException, StateAccessException,
               SerializationException;

    List<VIP> vipGetAll()
        throws StateAccessException, SerializationException;

    /**
     * ***********************************************************************
     * NEUTRON LBaaS calls.
     *
     * These will be backed by the same ZK managers as the above calls, but they
     * will have some additional logic specific to handling neutron API
     * handling. *************************************************************************
     */

    // Pools
    org.midonet.cluster.data.neutron.loadbalancer.Pool
    createNeutronPool(org.midonet.cluster.data.neutron.loadbalancer.Pool pool)
        throws StateAccessException, SerializationException;

    List<org.midonet.cluster.data.neutron.loadbalancer.Pool>
    createPoolBulk(
        List<org.midonet.cluster.data.neutron.loadbalancer.Pool> pool)
        throws StateAccessException, SerializationException;

    org.midonet.cluster.data.neutron.loadbalancer.Pool
    getPool(UUID id) throws StateAccessException, SerializationException;

    List<org.midonet.cluster.data.neutron.loadbalancer.Pool>
    getPools() throws StateAccessException, SerializationException;

    org.midonet.cluster.data.neutron.loadbalancer.Pool
    updatePool(UUID id, org.midonet.cluster.data.neutron.loadbalancer.Pool pool)
        throws StateAccessException, SerializationException;

    org.midonet.cluster.data.neutron.loadbalancer.Pool
    deletePool(UUID id) throws StateAccessException, SerializationException;

    // Members
    org.midonet.cluster.data.neutron.loadbalancer.Member
    createNeutronMember(
        org.midonet.cluster.data.neutron.loadbalancer.Member member)
        throws StateAccessException, SerializationException;

    List<org.midonet.cluster.data.neutron.loadbalancer.Member>
    createMemberBulk(
        List<org.midonet.cluster.data.neutron.loadbalancer.Member> member)
        throws StateAccessException, SerializationException;

    org.midonet.cluster.data.neutron.loadbalancer.Member
    getMember(UUID id) throws StateAccessException, SerializationException;

    List<org.midonet.cluster.data.neutron.loadbalancer.Member>
    getMembers() throws StateAccessException, SerializationException;

    org.midonet.cluster.data.neutron.loadbalancer.Member
    updateMember(UUID id,
                 org.midonet.cluster.data.neutron.loadbalancer.Member member)
        throws StateAccessException, SerializationException;

    org.midonet.cluster.data.neutron.loadbalancer.Member
    deleteMember(UUID id) throws StateAccessException, SerializationException;

    // Vips
    org.midonet.cluster.data.neutron.loadbalancer.VIP
    createNeutronVip(org.midonet.cluster.data.neutron.loadbalancer.VIP vip)
        throws StateAccessException, SerializationException;

    List<org.midonet.cluster.data.neutron.loadbalancer.VIP>
    createVipBulk(List<org.midonet.cluster.data.neutron.loadbalancer.VIP> vip)
        throws StateAccessException, SerializationException;

    org.midonet.cluster.data.neutron.loadbalancer.VIP
    getVip(UUID id) throws StateAccessException, SerializationException;

    List<org.midonet.cluster.data.neutron.loadbalancer.VIP>
    getVips() throws StateAccessException, SerializationException;

    org.midonet.cluster.data.neutron.loadbalancer.VIP
    updateVip(UUID id, org.midonet.cluster.data.neutron.loadbalancer.VIP vip)
        throws StateAccessException, SerializationException;

    org.midonet.cluster.data.neutron.loadbalancer.VIP
    deleteVip(UUID id) throws StateAccessException, SerializationException;

    // Health Monitors
    org.midonet.cluster.data.neutron.loadbalancer.HealthMonitor
    createNeutronHealthMonitor(
        org.midonet.cluster.data.neutron.loadbalancer.HealthMonitor healthMonitor)
        throws StateAccessException, SerializationException;

    List<org.midonet.cluster.data.neutron.loadbalancer.HealthMonitor>
    createHealthMonitorBulk(
        List<org.midonet.cluster.data.neutron.loadbalancer.HealthMonitor> healthMonitor)
        throws StateAccessException, SerializationException;

    org.midonet.cluster.data.neutron.loadbalancer.HealthMonitor
    getHealthMonitor(UUID id)
        throws StateAccessException, SerializationException;

    List<org.midonet.cluster.data.neutron.loadbalancer.HealthMonitor>
    getHealthMonitors() throws StateAccessException, SerializationException;

    org.midonet.cluster.data.neutron.loadbalancer.HealthMonitor
    updateHealthMonitor(UUID id,
                        org.midonet.cluster.data.neutron.loadbalancer.HealthMonitor healthMonitor)
        throws StateAccessException, SerializationException;

    org.midonet.cluster.data.neutron.loadbalancer.HealthMonitor
    deleteHealthMonitor(UUID id)
        throws StateAccessException, SerializationException;
}