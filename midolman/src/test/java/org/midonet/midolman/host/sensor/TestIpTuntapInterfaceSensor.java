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

package org.midonet.midolman.host.sensor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.midonet.midolman.host.interfaces.InterfaceDescription;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;


public class TestIpTuntapInterfaceSensor {

    @Test
    public void testUpdateInterfaceData() throws Exception {

        InterfaceSensor interfaceSensor = new IpTuntapInterfaceSensor() {
            @Override
            protected List<String> getTuntapOutput() {
                return Arrays.asList(
                    "xxx: tun",
                    "midovpn1: tap one_queue",
                    "midovpn3: tap one_queue",
                    "tapBridge4: tap",
                    "newRouterPort: tap"
                );
            }
        };

        Set<InterfaceDescription> interfaces = new HashSet<>();

        // Add one interface to test
        interfaces.add(new InterfaceDescription("xxx"));

        assertThat(
            "The end point was not updated",
            interfaces.iterator().next().getEndpoint(),
            is(InterfaceDescription.Endpoint.UNKNOWN));

        // Do the interface updating
        interfaceSensor.updateInterfaceData(interfaces);

        //Check that we parsed all the interfaces
        assertThat(interfaces.size(), equalTo(1));
        InterfaceDescription id = interfaces.iterator().next();

        assertThat(
            "The interface didn't have it's name changed",
            id.getName(), is("xxx"));

        assertThat(
            "The end point is parsed correctly",
            id.getEndpoint(),
            is(InterfaceDescription.Endpoint.TUNTAP));
    }

    @Test
    public void testDontUpdateNonTapInterfaceData() throws Exception {

        InterfaceSensor interfaceSensor = new IpTuntapInterfaceSensor() {
            @Override
            protected List<String> getTuntapOutput() {
                return Arrays.asList(
                    "xxy: tun",
                    "midovpn1: tap one_queue",
                    "midovpn3: tap one_queue",
                    "tapBridge4: tap",
                    "newRouterPort: tap"
                );
            }
        };

        Set<InterfaceDescription> interfaces = new HashSet<>();

        // Add one interface to test
        interfaces.add(new InterfaceDescription("bibiri"));

        // Do the interface updating
        interfaceSensor.updateInterfaceData(interfaces);

        //Check that we parsed all the interfaces
        assertThat(interfaces.size(), equalTo(1));

        assertThat(
            "The end point was not updated",
            interfaces.iterator().next().getEndpoint(),
            is(InterfaceDescription.Endpoint.UNKNOWN));

        // Do the interface updating
        interfaceSensor.updateInterfaceData(interfaces);

        //Check that we parsed all the interfaces
        assertThat(interfaces.size(), equalTo(1));
        InterfaceDescription id = interfaces.iterator().next();

        // Check the interface
        assertThat(
            "The interface didn't have it's name changed",
            id.getName(), is("bibiri"));

        assertThat(
            "The end point was not updated",
            id.getEndpoint(), is(
            InterfaceDescription.Endpoint.UNKNOWN));
    }
}
