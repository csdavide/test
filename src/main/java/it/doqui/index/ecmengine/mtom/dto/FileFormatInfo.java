/* Index ECM Engine - A system for managing the capture (when created
 * or received), classification (cataloguing), storage, retrieval,
 * revision, sharing, reuse and disposition of documents.
 *
 * Copyright (C) 2008 Regione Piemonte
 * Copyright (C) 2008 Provincia di Torino
 * Copyright (C) 2008 Comune di Torino
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package it.doqui.index.ecmengine.mtom.dto;

import java.util.Date;

/**
 * DTO che rappresenta il risultato del riconoscimento del formato di un
 * contenuto binario.
 * 
 * @author DoQui
 */
public class FileFormatInfo extends MtomEngineDto {
    public FileFormatInfo() {
	super();
    }

    public FileFormatInfo(String puid, String mimeType, String description, String formatVersion, int typeCode,
	    String typeDescription, String warning, Date identificationDate, String uid, String typeExtension) {
	super();
	this.puid = puid;
	this.mimeType = mimeType;
	this.description = description;
	this.formatVersion = formatVersion;
	this.typeCode = typeCode;
	this.typeDescription = typeDescription;
	this.warning = warning;
	this.identificationDate = identificationDate;
	this.uid = uid;
	this.typeExtension = typeExtension;
    }

    private static final long serialVersionUID = -7483047118029830285L;
    private String puid;
    private String mimeType;
    private String description;
    private String formatVersion;
    private int typeCode;
    private String typeDescription;
    private String warning;
    private Date identificationDate;
    private String uid;
    private String typeExtension;

    /**
     * Restituisce la descrizione del MIME type.
     */
    public String getDescription() {
	return description;
    }

    /**
     * Imposta la descrizione del MIME type.
     */
    public void setDescription(String description) {
	this.description = description;
    }

    /**
     * Restituisce il MIME type.
     */
    public String getMimeType() {
	return mimeType;
    }

    /**
     * Imposta il MIME type.
     */
    public void setMimeType(String mimeType) {
	this.mimeType = mimeType;
    }

    /**
     * Restituisce il PUID associato al tipo di file riconosciuto.
     */
    public String getPuid() {
	return puid;
    }

    /**
     * Imposta il PUID associato al tipo di file riconosciuto.
     */
    public void setPuid(String puid) {
	this.puid = puid;
    }

    /**
     * Restituisce la versione del formato riconosciuto.
     */
    public String getFormatVersion() {
	return formatVersion;
    }

    /**
     * Imposta la versione del formato riconosciuto.
     */
    public void setFormatVersion(String formatVersion) {
	this.formatVersion = formatVersion;
    }

    /**
     * Restituisce eventuali informazioni aggiuntive sul riconoscimento.
     */
    public String getWarning() {
	return warning;
    }

    /**
     * Imposta eventuali informazioni aggiuntive sul riconoscimento.
     */
    public void setWarning(String warning) {
	this.warning = warning;
    }

    /**
     * Restituisce un codice di tipo associato al formato.
     */
    public int getTypeCode() {
	return typeCode;
    }

    /**
     * Imposta un codice di tipo associato al formato.
     */
    public void setTypeCode(int typeCode) {
	this.typeCode = typeCode;
    }

    /**
     * Restituisce l'esito del processo di identificazione.
     */
    public String getTypeDescription() {
	return typeDescription;
    }

    /**
     * Imposta l'esito del processo di identificazione.
     */
    public void setTypeDescription(String typeDescription) {
	this.typeDescription = typeDescription;
    }

    /**
     * Restituisce il timestamp del riconoscimento.
     */
    public Date getIdentificationDate() {
	return identificationDate;
    }

    /**
     * Imposta il timestamp del riconoscimento.
     */
    public void setIdentificationDate(Date identificationDate) {
	this.identificationDate = identificationDate;
    }

    /**
     * Restituisce l'eventuale UID del nodo associato al riconoscimento.
     */
    public String getUid() {
	return uid;
    }

    /**
     * Imposta l'eventuale UID del nodo associato al riconoscimento.
     */
    public void setUid(String uid) {
	this.uid = uid;
    }

    /**
     * Restituisce l'estensione del formato riconosciuto.
     */
    public String getTypeExtension() {
	return typeExtension;
    }

    /**
     * Imposta l'estensione del formato riconosciuto.
     */
    public void setTypeExtension(String typeExtension) {
	this.typeExtension = typeExtension;
    }
}
