package it.doqui.index.ecmengine.mtom;

import it.doqui.libra.librabl.api.v1.cxf.ServiceProxy;

import jakarta.jws.WebService;
import jakarta.xml.ws.soap.MTOM;

@WebService
@MTOM
public interface ServiceImpl extends ServiceProxy {

}