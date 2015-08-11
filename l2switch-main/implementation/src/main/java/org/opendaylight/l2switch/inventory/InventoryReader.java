/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.l2switch.inventory;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.AddressCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.address.node.connector.Addresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.HostNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2switch.loopremover.rev140714.StpStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2switch.loopremover.rev140714.StpStatusAwareNodeConnector;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

/**
 * InventoryReader reads the opendaylight-inventory tree in MD-SAL data store.
 */
public class InventoryReader {

  private Logger _logger = LoggerFactory.getLogger(InventoryReader.class);
  private DataBroker dataService;

  /**
   * Construct an InventoryService object with the specified inputs.
   *
   * @param dataService The DataBrokerService associated with the InventoryService.
   */
  public InventoryReader(DataBroker dataService) {
    this.dataService = dataService;
  }

  /**
   * Get the NodeConnector on the specified node with the specified MacAddress observation.
   *
   * @param nodeInsId  InstanceIdentifier for the node on which to search for.
   * @param macAddress MacAddress to be searched for.
   * @return NodeConnectorRef that pertains to the NodeConnector containing the MacAddress observation.
   */
  public NodeConnectorRef getNodeConnector(InstanceIdentifier<Node> nodeInsId, MacAddress macAddress) {
    if(nodeInsId == null || macAddress == null) {
      return null;
    }

    NodeConnectorRef destNodeConnector = null;
    long latest = -1;
    ReadOnlyTransaction readOnlyTransaction = dataService.newReadOnlyTransaction();
    try {
      Optional<Node> dataObjectOptional = null;
      dataObjectOptional = readOnlyTransaction.read(LogicalDatastoreType.OPERATIONAL, nodeInsId).get();
      if(dataObjectOptional.isPresent()) {
        Node node = (Node) dataObjectOptional.get();
        _logger.debug("Looking address{} in node : {}", macAddress, nodeInsId);
        for(NodeConnector nc : node.getNodeConnector()) {
          // Don't look for mac in discarding node connectors
          StpStatusAwareNodeConnector saNodeConnector = nc.getAugmentation(StpStatusAwareNodeConnector.class);
          if(saNodeConnector != null && StpStatus.Discarding.equals(saNodeConnector.getStatus())) {
            continue;
          }
          _logger.debug("Looking address{} in nodeconnector : {}", macAddress, nc.getKey());
          AddressCapableNodeConnector acnc = nc.getAugmentation(AddressCapableNodeConnector.class);
          if(acnc != null) {
            List<Addresses> addressesList = acnc.getAddresses();
            for(Addresses add : addressesList) {
              if(macAddress.equals(add.getMac())) {
                if(add.getLastSeen() > latest) {
                  destNodeConnector = new NodeConnectorRef(nodeInsId.child(NodeConnector.class, nc.getKey()));
                  latest = add.getLastSeen();
                  _logger.debug("Found address{} in nodeconnector : {}", macAddress, nc.getKey());
                  break;
                }
              }
            }
          }
        }
      }
    } catch(InterruptedException e) {
      _logger.error("Failed to read nodes from Operation data store.");
      readOnlyTransaction.close();
      throw new RuntimeException("Failed to read nodes from Operation data store.", e);
    } catch(ExecutionException e) {
      _logger.error("Failed to read nodes from Operation data store.");
      readOnlyTransaction.close();
      throw new RuntimeException("Failed to read nodes from Operation data store.", e);
    }
    readOnlyTransaction.close();
    return destNodeConnector;
  }

  public NodeConnectorRef getNodeConnectorByMac(MacAddress macAddress) {
    NodeConnectorRef destNodeConnector = null;
    InstanceIdentifier<HostNode>
    hostId = InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology.class)
        .child(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology.class, 
            new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey(
                new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId("flow:1")))
        .child(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node.class, 
            new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey(
            new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId("host:" + macAddress.getValue())))
        .augmentation(HostNode.class).build();
    
    _logger.info("MacAddress:" + macAddress.getValue());
    ReadOnlyTransaction readOnlyTransaction = dataService.newReadOnlyTransaction();
    try {
      Optional<HostNode> dataObjectOptional = null;
      dataObjectOptional = readOnlyTransaction.read(LogicalDatastoreType.OPERATIONAL, hostId).get();
      if(dataObjectOptional.isPresent()) {
        HostNode hostNode = dataObjectOptional.get();
        _logger.info("AttKey:" + hostNode.getAttachmentPoints().get(0).getKey().getTpId());
        String attPoint = hostNode.getAttachmentPoints().get(0).getKey().getTpId().toString();
        InstanceIdentifier<NodeConnector>
        invNode = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, new NodeKey(new NodeId("openflow:" + attPoint.split(":")[1])))
            .child(NodeConnector.class, new NodeConnectorKey(
                new NodeConnectorId(attPoint)))
            .build();
        destNodeConnector = new NodeConnectorRef(invNode);
        _logger.info("Connector: " + destNodeConnector);
      }
    } catch(InterruptedException e) {
      _logger.error("Failed to read nodes from Operation data store.");
      readOnlyTransaction.close();
      throw new RuntimeException("Failed to read nodes from Operation data store.", e);
    } catch(ExecutionException e) {
      _logger.error("Failed to read nodes from Operation data store.");
      readOnlyTransaction.close();
      throw new RuntimeException("Failed to read nodes from Operation data store.", e);
    }
    
    return destNodeConnector;
  }
}
