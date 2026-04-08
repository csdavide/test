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
 * DTO che rappresenta un modello personalizzato dei dati.
 * 
 * @author DoQui
 */
public class CustomModel extends ModelDto {
    private static final long serialVersionUID = -7222205430414515141L;
    private String filename;
    private boolean active;
    private byte[] data;

    /**
     * Restituisce il contenuto binario del file del modello.
     */
    public byte[] getData() {
	return data;
    }

    /**
     * Imposta il contenuto binario del file del modello.
     */
    public void setData(byte[] data) {
	this.data = data;
    }

    /**
     * Restituisce il nome del file del modello.
     */
    public String getFilename() {
	return filename;
    }

    /**
     * Imposta il nome del file del modello.
     */
    public void setFilename(String filename) {
	this.filename = filename;
    }

    /**
     * Indica se il modello &agrave; attivo oppure no.
     */
    public boolean isActive() {
	return active;
    }

    /**
     * <p>
     * Imposta se il modello &agrave; attivo oppure no.
     * </p>
     * <p>
     * <b>Impostare sempre a {@code true} per evitare problemi di caricamento del
     * modello.</b>
     * </p>
     */
    public void setActive(boolean active) {
	this.active = active;
    }
}
