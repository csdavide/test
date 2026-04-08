package it.doqui.index.ecmengine.mtom.dto;

import jakarta.activation.DataHandler;
import jakarta.xml.bind.annotation.XmlMimeType;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Classe DTO che rappresenta un contenuto fisico.
 * 
 * @author DoQui
 *
 */
@SuppressWarnings("serial")
@XmlType
public class Attachment extends MtomEngineDto {
    /**
     * Il nome del file fisico.
     */
    public String fileName;

    /**
     * Il mimetype del file fisico.
     */
    public String fileType;

    /**
     * La dimensione del file fisico.
     */
    public long fileSize;


    /**
     * Il contenuto fisico.
     */
    @XmlMimeType("application/octet-stream")
    public DataHandler attachmentDataHandler;

    public Attachment() {
        super();
    }
}
