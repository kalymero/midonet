/*
 * Copyright 2014 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.midolman.simulation

import java.util.UUID

import scala.collection.JavaConversions._

import akka.actor.ActorSystem

import org.midonet.midolman.PacketWorkflow
import org.midonet.midolman.PacketWorkflow._
import org.midonet.midolman.rules.RuleResult
import org.midonet.midolman.simulation.Icmp.IPv4Icmp._
import org.midonet.midolman.state.FlowState
import org.midonet.midolman.topology.VirtualTopologyActor._
import org.midonet.midolman.topology.devices.{BridgePort, Port, RouterPort, VxLanPort}
import org.midonet.odp.flows._
import org.midonet.sdn.flows.VirtualActions.{FlowActionOutputToVrnBridge, FlowActionOutputToVrnPort}

object Coordinator {

    sealed trait ForwardAction extends SimulationResult
    case class ToPortAction(outPort: UUID) extends ForwardAction
    case class FloodBridgeAction(bridgeId: UUID, ports: List[UUID]) extends ForwardAction
    case class DoFlowAction(action: FlowAction) extends ForwardAction

    // This action is used when one simulation has to return N forward actions
    // A good example is when a bridge that has a vlan id set receives a
    // broadcast from the virtual network. It will output it to all its
    // materialized ports and to the logical port that connects it to the VAB
    case class ForkAction(actions: Seq[SimulationResult]) extends ForwardAction

    trait Device {

        /**
         * Process a packet described by the given match object. Note that the
         * Ethernet packet is the one originally ingressed the virtual network
         * - it does not reflect the changes made by other devices' handling of
         * the packet (whereas the match object does).
         *
         * @param pktContext The context for the simulation of this packet's
         * traversal of the virtual network. Use the context to subscribe
         * for notifications on the removal of any resulting flows, or to tag
         * any resulting flows for indexing.
         * @return An instance of Action that reflects what the device would do
         * after handling this packet (e.g. drop it, consume it, forward it).
         */
        def process(pktContext: PacketContext): SimulationResult
    }
}

/**
 * Coordinator object to simulate one packet traversing the virtual network.
 */
class Coordinator(context: PacketContext)
                 (implicit val actorSystem: ActorSystem) {

    import org.midonet.midolman.simulation.Coordinator._

    implicit val logPktCtx: PacketContext = context
    implicit val log = context.log
    private val MAX_DEVICES_TRAVERSED = 12

    // Used to detect loops: devices simulated (with duplicates).
    private var numDevicesSimulated = 0

    /**
     * Simulate the packet moving through the virtual topology. The packet
     * begins its journey through the virtual topology in one of these ways:
     * 1) it ingresses an exterior port of a virtual device (in which case the
     * packet arrives via the datapath switch from an entity outside the
     * virtual topology).
     * 2) it egresses an interior port of a virtual device (in which case the
     * packet was generated by that virtual device).
     *
     * In case 1, the match object for the packet was computed by the
     * FlowController and must contain an inPortID. If a wildcard flow is
     * eventually installed in the FlowController, the match will be a subset
     * of the match originally provided to the simulation. Note that in this
     * case the generatedPacketEgressPort argument should be null and will be
     * ignored.
     *
     * In case 2, the match object for the packet was computed by the Device
     * that emitted the packet and must not contain an inPortID. If the packet
     * is not dropped, it will eventually result in a packet being emitted
     * from one or more of the datapath ports. However, a flow is never
     * installed as a result of such a simulation. Note that in this case the
     * generatedPacketEgressPort argument must not be null.
     *
     * When the future returned by this method completes all the actions
     * resulting from the simulation (install flow and/or execute packet)
     * have been completed.
     *
     * The resulting future is never in a failed state.
     */
    def simulate(): SimulationResult = {
        log.debug("Simulating a packet")
        if (context.ingressed) {
            packetIngressesPort(context.inputPort, getPortGroups = true)
        } else {
            packetEgressesPort(context.egressPort)
        }
    }

    private def packetIngressesDevice(port: Port): SimulationResult = {
        val device = port match {
            case _: BridgePort => tryAsk[Bridge](port.deviceId)
            case _: VxLanPort => tryAsk[Bridge](port.deviceId)
            case _: RouterPort => tryAsk[Router](port.deviceId)
        }
        numDevicesSimulated += 1
        log.debug(s"packet ingresses port: ${port.id} at device ${port.deviceId}")
        handleAction(device.process(context))
    }


    private def mergeSimulationResults(first: SimulationResult,
                                       second: SimulationResult)
    : SimulationResult = {
        val result = (first, second) match {
            case (PacketWorkflow.Drop, action) => action
            case (action, PacketWorkflow.Drop) => action

            case (PacketWorkflow.TemporaryDrop, _) => TemporaryDrop
            case (_, PacketWorkflow.TemporaryDrop) => TemporaryDrop

            case (firstAction, secondAction) =>
                val clazz1 = firstAction.getClass
                val clazz2 = secondAction.getClass
                if (clazz1 != clazz2) {
                    log.error("Matching actions of different types {} & {}!",
                        clazz1, clazz2)
                }
                firstAction
        }
        log.debug(s"Forked action merged results $result")
        result
    }

    private def handleAction(simRes: SimulationResult): SimulationResult =
        simRes match {
            case DoFlowAction(act) => act match {
                case b: FlowActionPopVLAN =>
                    context.addVirtualAction(b)
                    val vlanId = context.wcmatch.getVlanIds.get(0)
                    context.wcmatch.removeVlanId(vlanId)
                    AddVirtualWildcardFlow
                case _ => NoOp
            }

            case ForkAction(acts) =>
                // Our handler for each action consists on evaluating
                // the Coordinator action and pairing it with the
                // original WMatch at that point in time
                // Will fail if run in parallel because of side-effects

                val originalMatch = context.origMatch.clone()
                // TODO: maybe replace with some other alternative that spares
                //       iterating the entire if we find the break cond
                val results = acts map { a =>
                    context.origMatch.reset(context.wcmatch)
                    handleAction(a)
                }

                context.origMatch.reset(originalMatch)
                results reduceLeft mergeSimulationResults

            case FloodBridgeAction(brId, ports) =>
                floodBridge(brId, ports)

            case ToPortAction(outPortID) =>
                packetEgressesPort(outPortID)

            case _ => simRes
        }

    private def packetIngressesPort(portID: UUID, getPortGroups: Boolean)
    : SimulationResult =
        // Avoid loops - simulate at most X devices.
        if (numDevicesSimulated >= MAX_DEVICES_TRAVERSED) {
            TemporaryDrop
        } else {
            val port = tryAsk[Port](portID)
            context.addFlowTag(port.deviceTag)
            port match {
                case p if !p.adminStateUp =>
                    processAdminStateDown(p, isIngress = true)
                case p =>
                    if (getPortGroups && p.isExterior) {
                        context.portGroups = p.portGroups
                    }
                    context.inPortId = portID
                    applyPortFilter(p, p.inboundFilter, packetIngressesDevice)
            }
        }

    private def applyPortFilter(port: Port, filterID: UUID,
                                thunk: (Port) => SimulationResult)
    : SimulationResult = {
        if (filterID == null)
            return thunk(port)

        val chain = tryAsk[Chain](filterID)
        val result = Chain.apply(chain, context, port.id, true)
        result.action match {
            case RuleResult.Action.ACCEPT =>
                thunk(port)
            case RuleResult.Action.DROP | RuleResult.Action.REJECT =>
                Drop
            case other =>
                log.error("Port filter {} returned {} which was " +
                        "not ACCEPT, DROP or REJECT.", filterID, other)
                TemporaryDrop
        }
    }

    /**
     * Simulate the packet egressing a virtual port. This is NOT intended
     * for flooding bridges
     */
    private def packetEgressesPort(portID: UUID): SimulationResult = {
        val port = tryAsk[Port](portID)
        context.addFlowTag(port.deviceTag)

        port match {
            case p if !p.adminStateUp =>
                processAdminStateDown(p, isIngress = false)
            case p =>
                context.outPortId = p.id
                applyPortFilter(p, p.outboundFilter, {
                    case p: Port if p.isExterior =>
                        emitFromPort(p)
                    case p: Port if p.isInterior =>
                        packetIngressesPort(p.peerId, getPortGroups = false)
                    case _ =>
                        log.warn("Port {} is unplugged", portID)
                        TemporaryDrop
                })
        }
    }

    /**
     * Complete the simulation by emitting the packet from the specified
     * virtual port.  If the packet was internally generated
     * this will do a SendPacket, otherwise it will do an AddWildcardFlow.
     */
    private def emitFromPort(port: Port): SimulationResult = {
        context.calculateActionsFromMatchDiff()
        log.debug("Emitting packet from vport {}", port.id)
        context.addVirtualAction(FlowActionOutputToVrnPort(port.id))
        emit(port.deviceId)
    }

    /**
     * Accumulates an output action for a port in the actions array buffer if
     * the port is exterior, active and its filter allows it
     */
    def applyExteriorPortFilter(portId: UUID): Boolean = {
        val port = tryAsk[Port](portId)
        context.addFlowTag(port.deviceTag)

        if (port.isExterior && port.adminStateUp &&
            port.active && port.id != context.inPortId) {

            if (port.outboundFilter ne null) {
                context.outPortId = portId
                val chain = tryAsk[Chain](port.outboundFilter)
                val result = Chain.apply(chain, context, port.id, true)
                log.debug(s"Chain ${chain.id} on port ${port.id} returned ${result.action}")
                result.action == RuleResult.Action.ACCEPT
            } else {
                true
            }
        } else {
            false
        }
    }

    /**
     * Complete the simulation by emitting the packet from the specified
     * list of virtual ports. If the packet was internally generated
     * this will do a SendPacket, otherwise it will do an AddWildcardFlow.
     */
    private def floodBridge(deviceId: UUID, ports: List[UUID]): SimulationResult = {
        context.calculateActionsFromMatchDiff()
        val filteredPorts = ports filter applyExteriorPortFilter
        if (filteredPorts.size > 0) {
            log.debug(s"Flooding $deviceId, ports $filteredPorts")
            context.addVirtualAction(FlowActionOutputToVrnBridge(deviceId, filteredPorts))
            emit(deviceId)
        } else {
            log.debug(s"Flooding $deviceId but no ports to forward to, dropping")
            Drop
        }
    }

    private def emit(deviceId: UUID): SimulationResult = {
        if (context.isGenerated) {
            SendPacket
        } else {
            context.trackConnection(deviceId)

            if (context.containsForwardStateKeys) {
                context.hardExpirationMillis = (FlowState.DEFAULT_EXPIRATION.toMillis / 2).toInt
            } else {
                context.idleExpirationMillis = IDLE_EXPIRATION_MILLIS
            }

            AddVirtualWildcardFlow
        }
    }

    private[this] def processAdminStateDown(port: Port, isIngress: Boolean)
    : SimulationResult = {
        port match {
            case p: RouterPort if isIngress =>
                sendIcmpProhibited(p)
            case p: RouterPort if context.inPortId != null =>
                tryAsk[Port](context.inPortId) match {
                    case p: RouterPort =>
                        sendIcmpProhibited(p)
                    case _ =>
                }
            case _ =>
        }

        Drop
    }

    private def sendIcmpProhibited(port: RouterPort): Unit = {
        val ethOpt = unreachableProhibitedIcmp(port, context.wcmatch, context.ethernet)
        if (ethOpt.nonEmpty) {
            context.addGeneratedPacket(port.id, ethOpt.get)
        }
    }
}
