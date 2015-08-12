package org.opendaylight.yang.gen.v1.urn.opendaylight.packet.loop.remover.impl.rev140528;

import java.util.Dictionary;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.l2switch.loopremover.flow.InitialFlowWriter;
import org.opendaylight.l2switch.loopremover.topology.NetworkGraphImpl;
import org.opendaylight.l2switch.loopremover.topology.NetworkGraphService;
import org.opendaylight.l2switch.loopremover.topology.TopologyLinkDataChangeHandler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoopRemoverModule
    extends org.opendaylight.yang.gen.v1.urn.opendaylight.packet.loop.remover.impl.rev140528.AbstractLoopRemoverModule {

  private final static Logger _logger = LoggerFactory.getLogger(LoopRemoverModule.class);
  private Registration listenerRegistration = null, invListenerReg = null;
  private TopologyLinkDataChangeHandler topologyLinkDataChangeHandler;

  public LoopRemoverModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier,
      org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
    super(identifier, dependencyResolver);
  }

  public LoopRemoverModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier,
      org.opendaylight.controller.config.api.DependencyResolver dependencyResolver,
      org.opendaylight.yang.gen.v1.urn.opendaylight.packet.loop.remover.impl.rev140528.LoopRemoverModule oldModule,
      java.lang.AutoCloseable oldInstance) {
    super(identifier, dependencyResolver, oldModule, oldInstance);
  }

  @Override
  public void customValidation() {
    // add custom validation form module attributes here.
  }

  @Override
  public java.lang.AutoCloseable createInstance() {
    NotificationProviderService notificationService = getNotificationServiceDependency();
    DataBroker dataService = getDataBrokerDependency();
    RpcProviderRegistry rpcRegistryDependency = getRpcRegistryDependency();
    SalFlowService salFlowService = rpcRegistryDependency.getRpcService(SalFlowService.class);

    // Write initial flows
    if (getIsInstallLldpFlow()) {
      _logger.info("LoopRemover will install an lldp flow");
      InitialFlowWriter initialFlowWriter = new InitialFlowWriter(salFlowService);
      initialFlowWriter.setFlowTableId(getLldpFlowTableId());
      initialFlowWriter.setFlowPriority(getLldpFlowPriority());
      initialFlowWriter.setFlowIdleTimeout(getLldpFlowIdleTimeout());
      initialFlowWriter.setFlowHardTimeout(getLldpFlowHardTimeout());
      invListenerReg = notificationService.registerNotificationListener(initialFlowWriter);
    }

    // Register Topology DataChangeListener
    NetworkGraphService networkGraphService = new NetworkGraphImpl();
    registerGlobalServiceWReg(NetworkGraphService.class, networkGraphService, null);
    this.topologyLinkDataChangeHandler = new TopologyLinkDataChangeHandler(dataService, networkGraphService);
    topologyLinkDataChangeHandler.setGraphRefreshDelay(getGraphRefreshDelay());
    topologyLinkDataChangeHandler.setTopologyId(getTopologyId());
    listenerRegistration = topologyLinkDataChangeHandler.registerAsDataChangeListener();

    final class CloseResources implements AutoCloseable {
      @Override
      public void close() throws Exception {
        if (listenerRegistration != null) {
          listenerRegistration.close();
        }
        if (invListenerReg != null) {
          invListenerReg.close();
        }
        _logger.info("LoopRemover (instance {}) torn down.", this);
      }
    }
    AutoCloseable ret = new CloseResources();
    _logger.info("LoopRemover (instance {}) initialized.", ret);
    return ret;

  }

  @SuppressWarnings("rawtypes")
  private ServiceRegistration registerGlobalServiceWReg(Class<?> clazz, Object instance,
      Dictionary<String, Object> properties) {
    try {
      BundleContext bCtx = FrameworkUtil.getBundle(instance.getClass()).getBundleContext();
      if (bCtx == null) {
        _logger.error("Could not retrieve the BundleContext");
        return null;
      }

      ServiceRegistration registration = bCtx.registerService(clazz.getName(), instance, properties);
      return registration;
    } catch (Exception e) {
      _logger.error("Exception {} while registering the service {}", e.getMessage(), instance.toString());
    }
    return null;
  }

}
