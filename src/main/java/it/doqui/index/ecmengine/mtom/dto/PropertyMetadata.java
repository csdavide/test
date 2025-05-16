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
 * DTO che rappresenta la definizione di una propriet&agrave; del modello dei
 * dati.
 * 
 * @author DoQui
 */
public class PropertyMetadata extends ModelComponentDTO {
    private static final long serialVersionUID = 2501999048668114583L;
    
    private String dataType;
    private boolean mandatory;
    private boolean multiValued;
    private boolean modifiable;

    /**
     * Restituisce il tipo della propriet&agrave;.
     */
    public String getDataType() {
	return dataType;
    }

    /**
     * Imposta il tipo della propriet&agrave;.
     */
    public void setDataType(String dataType) {
	this.dataType = dataType;
    }

    /**
     * Indica se la propriet&agrave; &egrave; obbligatoria oppure no.
     */
    public boolean isMandatory() {
	return mandatory;
    }

    /**
     * Imposta se la propriet&agrave; &egrave; obbligatoria oppure no.
     */
    public void setMandatory(boolean mandatory) {
	this.mandatory = mandatory;
    }

    /**
     * Indica se la propriet&agrave; &egrave; multivalore oppure no.
     */
    public boolean isMultiValued() {
	return multiValued;
    }

    /**
     * Imposta se la propriet&agrave; &egrave; multivalore oppure no.
     */
    public void setMultiValued(boolean multiValued) {
	this.multiValued = multiValued;
    }

    /**
     * Indica se la propriet&agrave; &egrave; modificabile oppure no.
     */
    public boolean isModifiable() {
	return modifiable;
    }

    /**
     * Imposta se la propriet&agrave; &egrave; modificabile oppure no.
     */
    public void setModifiable(boolean modifiable) {
	this.modifiable = modifiable;
    }

}
