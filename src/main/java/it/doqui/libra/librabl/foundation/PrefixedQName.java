package it.doqui.libra.librabl.foundation;

import org.apache.commons.lang3.StringUtils;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

public class PrefixedQName extends QName {

    public PrefixedQName(String namespaceURI, String localPart) {
        super(namespaceURI, localPart);
    }

    public static PrefixedQName valueOf(String s) {
        String[] x = StringUtils.stripToEmpty(s).split(":", 2);
        if (x.length > 1) {
            return new PrefixedQName(x[0], x[1]);
        } else {
            return new PrefixedQName("", x[0]);
        }
    }

    public boolean hasNamespace() {
        return !this.getNamespaceURI().equals(XMLConstants.NULL_NS_URI);
    }

    @Override
    public String toString() {
        if (this.getNamespaceURI().equals(XMLConstants.NULL_NS_URI)) {
            return this.getLocalPart();
        } else {
            return this.getNamespaceURI() + ":" + this.getLocalPart();
        }
    }
}
