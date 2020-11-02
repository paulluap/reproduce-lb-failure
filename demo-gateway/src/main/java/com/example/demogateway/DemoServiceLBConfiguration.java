package com.example.demogateway;

import java.time.Duration;
import java.util.List;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.DelegatingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import reactor.core.publisher.Flux;

@Configuration
//TODO: apply this globally
@LoadBalancerClient(value="demo-service", configuration = CustomLoadBalancerConfiguration.class)
public class DemoServiceLBConfiguration { }

class CustomLoadBalancerConfiguration { 

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

class RefresheableDSInstanceListSuppier extends DelegatingServiceInstanceListSupplier {

	public RefresheableDSInstanceListSuppier(ServiceInstanceListSupplier delegate) {
		super(delegate);
	}
	
	//this cannot handle RefreshRoutesEvent, so we rely on periodically reloading the service instances
	@Override
	public Flux<List<ServiceInstance>> get() {
		return Flux.defer(delegate).doOnNext(list->{
			System.out.println("refetched service instances from discovery client service instance list supplier");
		}).repeatWhen(reload->reload.delayElements(Duration.ofSeconds(30)));
	}

}