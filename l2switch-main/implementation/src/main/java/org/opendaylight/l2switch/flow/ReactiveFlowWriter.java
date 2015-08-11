/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.l2switch.flow;

import org.opendaylight.l2switch.inventory.InventoryReader;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.arp.rev140528.ArpPacketListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.arp.rev140528.ArpPacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.arp.rev140528.arp.packet.received.packet.chain.packet.ArpPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.PacketChain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.packet.chain.packet.RawPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.ethernet.packet.received.packet.chain.packet.EthernetPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class listens to certain type of packets and writes
 * a mac to mac flows.
 */
public class ReactiveFlowWriter implements ArpPacketListener {
  private InventoryReader inventoryReader;
  private FlowWriterService flowWriterService;
  private final MacAddress MAC_TO_IGNORE = new MacAddress("ff:ff:ff:ff:ff:ff");
  private static final Logger _logger = LoggerFactory.getLogger(ReactiveFlowWriter.class);

  public ReactiveFlowWriter(InventoryReader inventoryReader, FlowWriterService flowWriterService) {
    this.inventoryReader = inventoryReader;
    this.flowWriterService = flowWriterService;
  }

  @Override
  public void onArpPacketReceived(ArpPacketReceived packetReceived) {
    if(packetReceived == null || packetReceived.getPacketChain() == null) {
      return;
    }
    _logger.info("Reveice ARP Packet: " + packetReceived);

    RawPacket rawPacket = null;
    EthernetPacket ethernetPacket = null;
    ArpPacket arpPacket = null;
    for(PacketChain packetChain : packetReceived.getPacketChain()) {
      if(packetChain.getPacket() instanceof RawPacket) {
        rawPacket = (RawPacket) packetChain.getPacket();
      } else if(packetChain.getPacket() instanceof EthernetPacket) {
        ethernetPacket = (EthernetPacket) packetChain.getPacket();
      } else if(packetChain.getPacket() instanceof ArpPacket) {
        arpPacket = (ArpPacket) packetChain.getPacket();
      }
    }
    if(rawPacket == null || ethernetPacket == null || arpPacket == null) {
      return;
    }
    MacAddress destMac = ethernetPacket.getDestinationMac();
    if(!MAC_TO_IGNORE.equals(destMac)) {
      writeFlowsDijkstra(rawPacket.getIngress(),
          ethernetPacket.getSourceMac(),
          ethernetPacket.getDestinationMac());
    }
  }

  /**
   * Invokes flow writer service to write  bidirectional mac-mac flows on a switch.
   *
   * @param ingress The NodeConnector where the payload came from.
   * @param srcMac  The source MacAddress of the packet.
   * @param destMac The destination MacAddress of the packet.
   */
  public void writeFlows(NodeConnectorRef ingress, MacAddress srcMac, MacAddress destMac) {
	_logger.info("Write Flow: " + ingress);
	NodeConnectorRef destNodeConnector = inventoryReader.getNodeConnector(ingress.getValue().firstIdentifierOf(Node.class), destMac);
    if(destNodeConnector != null) {
      flowWriterService.addBidirectionalMacToMacFlows(srcMac, ingress, destMac, destNodeConnector);
    }
  }

  /**
   * Invokes flow writer service to write  bidirectional mac-mac flows to switchers by Dijkstra.
   *
   * @param ingress The NodeConnector where the payload came from.
   * @param srcMac  The source MacAddress of the packet.
   * @param destMac The destination MacAddress of the packet.
   */
  public void writeFlowsDijkstra(NodeConnectorRef ingress, MacAddress srcMac, MacAddress destMac) {
	_logger.info("Write Flow Mac: " + ingress);
	NodeConnectorRef destNodeConnector = inventoryReader.getNodeConnectorByMac(destMac);
    if(destNodeConnector != null) {
      flowWriterService.addBidirectionalMacToMacFlows(srcMac, ingress, destMac, destNodeConnector);
    }
  }
}
