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
 * DTO che permette di indicare informazioni aggiuntive di un nodo.
 * 
 * @author DoQui
 */
public class NodeInfo extends MtomEngineDto {
    private static final long serialVersionUID = -32440983873885128L;
    private String prefixedName;
    private boolean enveloped;

    /**
     * Costruttore predefinito.
     */
    public NodeInfo() {
	super();
    }

    /**
     * Genera il DTO dato un prefixed name.
     */
    public NodeInfo(String prefixedName) {
	this.prefixedName = prefixedName;
    }

    public NodeInfo(String prefixedName, boolean enveloped) {
	super();
	this.prefixedName = prefixedName;
	this.enveloped = enveloped;
    }

    /**
     * Restituisce il prefixed name.
     */
    public String getPrefixedName() {
	return prefixedName;
    }

    /**
     * Imposta il prefixed name.
     */
    public void setPrefixedName(String prefixedName) {
	this.prefixedName = prefixedName;
    }

    /**
     * Indica se il nodo &egrave; imbustato.
     */
    public boolean isEnveloped() {
	return enveloped;
    }

    /**
     * Imposta se il nodo &egrave; imbustato.
     */
    public void setEnveloped(boolean enveloped) {
	this.enveloped = enveloped;
    }
}
