package it.doqui.libra.librabl.business.provider.security;

import io.quarkus.arc.Arc;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.exceptions.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor;
import org.apache.cxf.frontend.WSDLGetInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.Phase;
import org.eclipse.microprofile.config.ConfigProvider;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.util.Optional;

@Slf4j
public class SecuritySoapInterceptor extends AbstractSoapInterceptor {

    public SecuritySoapInterceptor() {
        super(Phase.PRE_PROTOCOL);
        addBefore(WSDLGetInterceptor.class.getName());
    }

    @Override
    public void handleMessage(SoapMessage soapMessage) throws Fault {
        var tokenQname = ConfigProvider.getConfig()
            .getOptionalValue("libra.authentication.cxf-token-header", String.class)
            .map(QName::valueOf)
            .orElse(QName.valueOf("token"));
        var securityToken = getTokenFromHeader(soapMessage, tokenQname);
        if (securityToken != null) {
            var authenticationService = Arc.container().select(AuthenticationManager.class).get();
            authenticationService.authenticateWithToken(securityToken, null);
        } else {
            var username = getTokenFromHeader(soapMessage, QName.valueOf("username"));
            var password = getTokenFromHeader(soapMessage, QName.valueOf("password"));
            if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
                var authenticationService = Arc.container().select(AuthenticationManager.class).get();
                authenticationService.authenticateUser(AuthorityRef.valueOf(username), Optional.of(password));
            } else {
                throw new UnauthorizedException("Unable to find security soap header " + tokenQname);
            }
        }
    }

    private String getTokenFromHeader(SoapMessage message, QName tokenQname) {
        String securityToken = null;
        try {
            var list = message.getHeaders();
            for(var h : list) {
                if (h.getName().equals(tokenQname)) {
                    Element token = (Element)h.getObject();
                    if(token != null) {
                        securityToken = token.getTextContent();
                        break;
                    }
                }
            }
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw new UnauthorizedException("Invalid user: " + e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UnauthorizedException("Security Token failure: " + e.getMessage());
        }

        return securityToken;
    }
}
