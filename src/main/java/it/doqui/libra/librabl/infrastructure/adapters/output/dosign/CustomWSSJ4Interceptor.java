package it.doqui.libra.librabl.infrastructure.adapters.output.dosign;

import io.quarkus.arc.Arc;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import java.util.HashMap;
import java.util.List;

public class CustomWSSJ4Interceptor extends AbstractPhaseInterceptor<SoapMessage> {

    private final String customer;

    public CustomWSSJ4Interceptor(String customer) {
        super(Phase.PREPARE_SEND);
        this.customer = customer;
    }

    @Override
    public void handleMessage(SoapMessage message) throws Fault {
        var customHeaders = new HashMap<>();
        var sessionContext = Arc.container().select(SessionContext.class).get();
        customHeaders.put("X-ClientProfile", List.of(new CredentialHeader(sessionContext.getTenant(), customer).toJsonString()));
        message.put(Message.PROTOCOL_HEADERS, customHeaders);
    }
}
