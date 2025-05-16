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

/**
 * DTO che rappresenta un file imbustato.
 * 
 * @author DoQui
 */
public class EnvelopedContent extends MtomEngineDto {
    private static final long serialVersionUID = -2127365555552306198L;
    private byte[] data;

    public EnvelopedContent() {
	super();
    }

    public EnvelopedContent(byte[] data, byte[] detachedData, boolean store) {
	super();
	this.data = data;
	this.detachedData = detachedData;
	this.store = store;
    }

    private byte[] detachedData;
    private boolean store;

    /**
     * Restituisce il contenuto binario del file imbustato, o del file originale in
     * caso di busta separata.
     */
    public byte[] getData() {
	return data;
    }

    /**
     * Imposta il contenuto binario del file imbustato, o del file originale in caso
     * di busta separata.
     */
    public void setData(byte[] data) {
	this.data = data;
    }

    /**
     * Indica se il file dovr&agrave essere salvato sul tenant dopo l'operazione
     * richiesta.
     */
    public boolean isStore() {
	return store;
    }

    /**
     * Imposta se il file dovr&agrave essere salvato sul tenant dopo l'operazione
     * richiesta.
     */
    public void setStore(boolean store) {
	this.store = store;
    }

    /**
     * Restituisce il contenuto binario dell'eventuale busta separata.
     */
    public byte[] getDetachedData() {
	return detachedData;
    }

    /**
     * Imposta il contenuto binario dell'eventuale busta separata.
     */
    public void setDetachedData(byte[] detachedData) {
	this.detachedData = detachedData;
    }
}
