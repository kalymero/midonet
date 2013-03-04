/*
 * Copyright 2011 Midokura KK
 */

package org.midonet.midolman.rules;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.midonet.midolman.layer4.NatMapping;
import org.midonet.midolman.layer4.NwTpPair;
import org.midonet.midolman.rules.RuleResult.Action;
import org.midonet.packets.ICMP;
import org.midonet.packets.IPv4;
import org.midonet.packets.IPv4Addr;
import org.midonet.packets.MalformedPacketException;
import org.midonet.packets.TCP;
import org.midonet.packets.UDP;
import org.midonet.sdn.flows.WildcardMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ReverseNatRule extends NatRule {

    private final static Logger log = LoggerFactory
            .getLogger(ReverseNatRule.class);

    public ReverseNatRule(Condition condition, Action action, boolean dnat) {
        super(condition, action, dnat);
    }

    // default constructor for the JSON serialization.
    public ReverseNatRule() {
        super();
    }

    public ReverseNatRule(Condition condition, Action action, UUID chainId,
            int position, boolean dnat) {
        super(condition, action, chainId, position, dnat);
    }

    @Override
    public void apply(ChainPacketContext fwdInfo, RuleResult res,
                      NatMapping natMapping) {

        if (!isNatSupported(res.pmatch))
            return;

        if (natMapping == null)
            log.error("Expected NAT mapping to exist");
        else {
            try {
                if (dnat)
                    applyReverseDnat(res, natMapping);
                else
                    applyReverseSnat(res, natMapping);
            } catch (MalformedPacketException e) {
                log.error("Errors applying reverse NAT {}", e);
            }
        }
    }

    /**
     * This is an aux. method for applyReverseXnat that applies the translation
     * inside ICMP error data fields: these contain the original IP header and
     * TCP/UDP data so they need to be altered according with the translation
     *
     * @param origConn
     * @param match
     * @param isSnat
     */
    private void applyReverseNatToICMPData(NwTpPair origConn,
                                           WildcardMatch match, boolean isSnat)
        throws MalformedPacketException {

        int icmpType = match.getTransportSource();
        if (icmpType == ICMP.TYPE_ECHO_REPLY ||
            icmpType == ICMP.TYPE_ECHO_REQUEST) {
            // this is a plain translation
            if (isSnat) {
                match.setNetworkDestination(
                    new IPv4Addr().setIntAddress(origConn.nwAddr));
            } else {
                match.setNetworkSource(
                    new IPv4Addr().setIntAddress(origConn.nwAddr));
            }
            return;
        }

        if (icmpType != ICMP.TYPE_PARAMETER_PROBLEM &&
            icmpType != ICMP.TYPE_TIME_EXCEEDED &&
            icmpType != ICMP.TYPE_UNREACH) {
            // other than these types should not even be coming through NAT
            return;
        }

        // ICMP error data contains an IP packet + part of its payload
        byte[] data = match.getIcmpData();
        int dataSize = data.length;
        ByteBuffer bb = ByteBuffer.wrap(data);
        IPv4 header = new IPv4();
        header.deserializeHeader(bb);
        if (isSnat) {
            header.setSourceAddress(origConn.nwAddr);
            match.setNetworkDestination(
                new IPv4Addr().setIntAddress(origConn.nwAddr));
        } else {
            header.setDestinationAddress(origConn.nwAddr);
            match.setNetworkSource(
                new IPv4Addr().setIntAddress(origConn.nwAddr));
        }
        int ipHeadSize = dataSize - bb.remaining();

        // What's left inside bb is the IP payload with the orig. message
        short tpSrc = bb.getShort();
        short tpDst = bb.getShort();
        switch (header.getProtocol()) {
            case TCP.PROTOCOL_NUMBER:
            case UDP.PROTOCOL_NUMBER:
                if (isSnat) {
                    tpSrc = (short)origConn.tpPort;
                } else {
                    tpDst = (short)origConn.tpPort;
                }
                break;
            // case ICMP it's not really ports, but we can just copy the bytes
        }

        // construct new ICMP data field and replace
        ByteBuffer natBB = ByteBuffer.allocate(data.length);
        natBB.put(header.serialize(), 0, ipHeadSize);
        natBB.putShort(tpSrc);
        natBB.putShort(tpDst);
        natBB.put(bb);
        match.setIcmpData(natBB.array());
    }

    private void applyReverseDnat(RuleResult res, NatMapping natMapping)
        throws MalformedPacketException {

        WildcardMatch match = res.pmatch;

        NatLookupTuple tp = getTpForMappingLookup(match);
        NwTpPair origConn = natMapping.lookupDnatRev(tp.proto,
                                                     tp.nwDst, tp.tpDst,
                                                     tp.nwSrc, tp.tpSrc);
        if (null == origConn)
            return;

        log.debug("Found reverse DNAT. Use SRC {}:{} for flow from {}:{} to "
                + "{}:{}, protocol {}", new Object[] {
                IPv4.fromIPv4Address(origConn.nwAddr), origConn.tpPort & USHORT,
                IPv4.fromIPv4Address(tp.nwSrc), tp.tpSrc,
                IPv4.fromIPv4Address(tp.nwDst), tp.tpDst, tp.proto});
        if (match.getNetworkProtocol() == ICMP.PROTOCOL_NUMBER) {
            applyReverseNatToICMPData(origConn, match, false);
        } else {
            match.setNetworkSource(
                new IPv4Addr().setIntAddress(origConn.nwAddr));
            match.setTransportSource(origConn.tpPort);
        }
        res.action = action;
    }

    private void applyReverseSnat(RuleResult res, NatMapping natMapping)
        throws MalformedPacketException {

        WildcardMatch match = res.pmatch;
        NatLookupTuple tp = getTpForMappingLookup(match);

        NwTpPair origConn = natMapping.lookupSnatRev(tp.proto,
                                                     tp.nwDst, tp.tpDst,
                                                     tp.nwSrc, tp.tpSrc);
        if (null == origConn)
            return;

        log.debug("Found reverse SNAT. Use DST {}:{} for flow from {}:{} to "
                      + "{}:{}, protocol", new Object[]{
            IPv4.fromIPv4Address(origConn.nwAddr), origConn.tpPort & USHORT,
            IPv4.fromIPv4Address(tp.nwSrc), tp.tpSrc,
            IPv4.fromIPv4Address(tp.nwDst), tp.tpDst, tp.proto});
        if (match.getNetworkProtocol() == ICMP.PROTOCOL_NUMBER) {
            applyReverseNatToICMPData(origConn, match, true);
        } else {
            match.setNetworkDestination(
                new IPv4Addr().setIntAddress(origConn.nwAddr));
            match.setTransportDestination(origConn.tpPort);
        }
        res.action = action;
    }

    @Override
    public int hashCode() {
        return super.hashCode() * 29 + "ReverseNatRule".hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof ReverseNatRule))
            return false;
        return super.equals(other);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ReverseNatRule [");
        sb.append(super.toString()).append("]");
        return sb.toString();
    }

}
