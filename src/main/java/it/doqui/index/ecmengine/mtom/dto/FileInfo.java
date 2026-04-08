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
 * DTO che rappresenta un contenuto binario su cui si vogliono eseguire delle
 * operazioni di gestione documentale.
 * 
 * @author DoQui
 */
public class FileInfo extends MtomEngineDto {
    private static final long serialVersionUID = 3199948404193643720L;
    private String name;
    private byte[] contents;
    private boolean store = false;
    private String path;
    private long size;
    private Date modified;

    public FileInfo() {
	super();
    }

    public FileInfo(String name, byte[] contents, boolean store, String path, long size, Date modified) {
	super();
	this.name = name;
	this.contents = contents;
	this.store = store;
	this.path = path;
	this.size = size;
	this.modified = modified;
    }

    /**
     * Indica se il contenuto deve essere memorizzato nel tenant temporaneo.
     */
    public boolean isStore() {
	return store;
    }

    /**
     * Imposta se il contenuto deve essere memorizzato nel tenant temporaneo.
     */
    public void setStore(boolean store) {
	this.store = store;
    }

    /**
     * Restituisce il contenuto binario.
     */
    public byte[] getContents() {
	return contents;
    }

    /**
     * Imposta il contenuto binario.
     */
    public void setContents(byte[] contents) {
	this.contents = contents;
    }

    /**
     * Restituisce il nome del file associato al contenuto binario.
     */
    public String getName() {
	return name;
    }

    /**
     * Imposta il nome del file associato al contenuto binario.
     */
    public void setName(String name) {
	this.name = name;
    }

    /**
     * Restituisce il percorso del file associato al contenuto binario.
     */
    public String getPath() {
	return path;
    }

    /**
     * Imposta il percorso del file associato al contenuto binario.
     */
    public void setPath(String path) {
	this.path = path;
    }

    /**
     * Restituisce la dimensione in byte del contenuto binario.
     */
    public long getSize() {
	return size;
    }

    /**
     * Imposta la dimensione in byte del contenuto binario.
     */
    public void setSize(long size) {
	this.size = size;
    }

    /**
     * Restituisce la data di ultima modifica del file associato al contenuto
     * binario.
     */
    public Date getModified() {
	return modified;
    }

    /**
     * Imposta la data di ultima modifica del file associato al contenuto binario.
     */
    public void setModified(Date modified) {
	this.modified = modified;
    }
}
