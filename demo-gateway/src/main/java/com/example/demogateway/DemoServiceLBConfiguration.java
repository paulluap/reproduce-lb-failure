package com.example.demogateway;

import java.time.Duration;
import java.util.List;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import reactor.core.publisher.Flux;

@Configuration
//TODO: apply this globally
@LoadBalancerClient(value="demo-service", configuration = CustomLoadBalancerConfiguration.class)
public class DemoServiceLBConfiguration {

}

class CustomLoadBalancerConfiguration { 

	@Bean
	public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(
			Environment environment,
			LoadBalancerClientFactory loadBalancerClientFactory) {
		String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
		return new RoundRobinLoadBalancer(loadBalancerClientFactory.getLazyProvider(name,
				ServiceInstanceListSupplier.class), name);
	}
	
	@Bean
	public ServiceInstanceListSupplier healthCheckDiscoveryClientServiceInstanceListSupplier(
			ConfigurableApplicationContext context, Environment env) {
		ReactiveDiscoveryClient discoveryClient = context
				.getBean(ReactiveDiscoveryClient.class);
		
		DiscoveryClientServiceInstanceListSupplier dsListSupplier = new DiscoveryClientServiceInstanceListSupplier(discoveryClient, env);
		return ServiceInstanceListSupplier
				.builder()
				//.withDiscoveryClient()
				.withBase(new RefresheableDSInstanceListSuppier(dsListSupplier))
				.withHealthChecks().withCaching().build(context);
	}
	
}

class RefresheableDSInstanceListSuppier implements ServiceInstanceListSupplier {

	private final ServiceInstanceListSupplier delegate;

	RefresheableDSInstanceListSuppier(DiscoveryClientServiceInstanceListSupplier delegate){
		this.delegate = delegate;
	}
	
	//delegate service instance flux is created with Defer, when the deferred stream is repeated delegate.getInstances(serviceId) is rerun and returns the latest service instances
	//this cannot handle RefreshRoutesEvent, so we rely on periodically reloading the service instances
	@Override
	public Flux<List<ServiceInstance>> get() {
		return delegate.get().doOnNext(list->{
			System.out.println("refetched service instances from discovery client service instance list supplier");
		}).repeatWhen(reload->reload.delayElements(Duration.ofSeconds(30)));
	}

	@Override
	public String getServiceId() {
		return delegate.getServiceId();
	}
	
}