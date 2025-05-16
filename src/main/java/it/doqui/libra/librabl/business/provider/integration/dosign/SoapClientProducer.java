package it.doqui.libra.librabl.business.provider.integration.dosign;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
public class SoapClientProducer {

    @ConfigProperty(name = "libra.customer", defaultValue = "LOCAL")
    String customer;

    public <T> T createClient(Class<T> clazz, String wsdlAddress) {
        log.debug("Creating client for {}...", clazz.getName());
        var factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(clazz);
        factory.setAddress(wsdlAddress);

        @SuppressWarnings("unchecked")
        T client = (T) factory.create();

        var proxy = ClientProxy.getClient(client);
        proxy.getOutInterceptors().add(new CustomWSSJ4Interceptor(customer));
        log.debug("Added interceptor for {}", clazz.getName());
        return client;
    }
}
