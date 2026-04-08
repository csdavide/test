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

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * DTO che rappresenta un file da processare.
 * 
 * @author DoQui
 */
public class Document extends MtomEngineDto {
    private static final long serialVersionUID = 4938983819136923577L;

    //@JsonIgnore
    private byte[] buffer;
    private String uid;
    private String contentPropertyPrefixedName;
    private DocumentOperation operation;

    public Document() {
	super();
    }

    public Document(byte[] buffer, String uid, String contentPropertyPrefixedName, DocumentOperation operation) {
	super();
	this.buffer = buffer;
	this.uid = uid;
	this.contentPropertyPrefixedName = contentPropertyPrefixedName;
	this.operation = operation;
    }

    /**
     * Restituisce il contenuto binario del file.
     */
    public byte[] getBuffer() {
	return buffer;
    }

    /**
     * Imposta il contenuto binario del file.
     */
    public void setBuffer(byte[] buffer) {
	this.buffer = buffer;
    }

    /**
     * Restituisce l'eventuale UID del nodo del file.
     */
    public String getUid() {
	return uid;
    }

    /**
     * Imposta l'eventuale UID del nodo del file.
     */
    public void setUid(String uid) {
	this.uid = uid;
    }

    /**
     * Restituisce le operazioni da eseguire sul file.
     */
    public DocumentOperation getOperation() {
	return operation;
    }

    /**
     * Imposta le operazioni da eseguire sul file.
     */
    public void setOperation(DocumentOperation operation) {
	this.operation = operation;
    }

    /**
     * Restituisce l'eventuale prefixed name della propriet&agrave; che rappresenta
     * il contenuto binario del nodo del file.
     */
    public String getContentPropertyPrefixedName() {
	return contentPropertyPrefixedName;
    }

    /**
     * Imposta l'eventuale prefixed name della propriet&agrave; che rappresenta il
     * contenuto binario del nodo del file.
     */
    public void setContentPropertyPrefixedName(String contentPropertyPrefixedName) {
	this.contentPropertyPrefixedName = contentPropertyPrefixedName;
    }
}
