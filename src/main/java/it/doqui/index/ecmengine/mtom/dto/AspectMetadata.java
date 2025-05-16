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
 * DTO che rappresenta la definizione di un aspetto (aspect) del modello dei
 * dati.
 * 
 * @author DoQui
 */
public class AspectMetadata extends ModelComponentDTO {
    private static final long serialVersionUID = -9085785346781343256L;
    
    private PropertyMetadata[] properties;
    private AssociationMetadata[] associations;
    private ChildAssociationMetadata[] childAssociations;
    private String parentPrefixedName;

    /**
     * Restituisce le definizioni delle associazioni sorgente-destinazione
     * dell'aspetto.
     */
    public AssociationMetadata[] getAssociations() {
	return associations;
    }

    /**
     * Imposta le definizioni delle associazioni sorgente-destinazione dell'aspetto.
     */
    public void setAssociations(AssociationMetadata[] associations) {
	this.associations = associations;
    }

    /**
     * Restituisce le definizioni delle associazioni padre-figlio dell'aspetto.
     */
    public ChildAssociationMetadata[] getChildAssociations() {
	return childAssociations;
    }

    /**
     * Imposta le definizioni delle associazioni padre-figlio dell'aspetto.
     */
    public void setChildAssociations(ChildAssociationMetadata[] childAssociations) {
	this.childAssociations = childAssociations;
    }

    /**
     * Restituisce le definizioni delle propriet&agrave; dell'aspetto.
     */
    public PropertyMetadata[] getProperties() {
	return properties;
    }

    /**
     * Imposta le definizioni delle propriet&agrave; dell'aspetto.
     */
    public void setProperties(PropertyMetadata[] properties) {
	this.properties = properties;
    }

    /**
     * Restituisce il prefixed name dell'aspetto padre, se presente, altrimenti
     * {@code null}.
     */
    public String getParentPrefixedName() {
	return parentPrefixedName;
    }

    /**
     * Imposta il prefixed name dell'aspetto padre, se presente.
     */
    public void setParentPrefixedName(String parentPrefixedName) {
	this.parentPrefixedName = parentPrefixedName;
    }

}
