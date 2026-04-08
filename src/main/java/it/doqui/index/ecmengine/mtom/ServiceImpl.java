package it.doqui.index.ecmengine.mtom;

import it.doqui.libra.librabl.infrastructure.adapters.input.index.cxf.ServiceProxy;
import jakarta.jws.WebService;
import jakarta.xml.ws.soap.MTOM;

@WebService
@MTOM
public interface ServiceImpl extends ServiceProxy {

}