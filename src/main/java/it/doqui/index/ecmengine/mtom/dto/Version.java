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
import java.util.Vector;

//import org.alfresco.service.cmr.version.VersionType;

/**
 * DTO che rappresenta la versione di un nodo.
 * 
 * @author DoQui
 */
public class Version extends MtomEngineDto {
    private static final long serialVersionUID = 1100135075523489548L;

    public Version(String creator, Date createdDate, String description, String versionLabel, Node versionedNode,
	    Property[] versionProperties) {
	super();
	this.versionProperties = new Vector<Property>();
	this.creator = creator;
	this.createdDate = createdDate;
	this.description = description;
	this.versionLabel = versionLabel;
	this.versionedNode = versionedNode;
	this.setVersionProperties(versionProperties);
    }

    private String creator;
    private Date createdDate;
    private String description;
    private String versionLabel;
    private Node versionedNode;
    private Vector<Property> versionProperties;

    /**
     * <p>
     * Costruttore predefinito.
     * </p>
     * <p>
     * Inizializza il vettore delle propriet&agrave;.
     * </p>
     */
    public Version() {
	super();
	this.versionProperties = new Vector<Property>();
    }

    /**
     * Restituisce la data di creazione della versione.
     */
    public Date getCreatedDate() {
	return this.createdDate;
    }

    /**
     * Imposta la data di creazione della versione.
     */
    public void setCreatedDate(Date createdDate) {
	this.createdDate = createdDate;
    }

    /**
     * Restituisce il nome utente del creatore della versione.
     */
    public String getCreator() {
	return this.creator;
    }

    /**
     * Imposta il nome utente del creatore della versione.
     */
    public void setCreator(String creator) {
	this.creator = creator;
    }

    /**
     * Restituisce la descrizione della versione.
     */
    public String getDescription() {
	return this.description;
    }

    /**
     * Imposta la descrizione della versione.
     */
    public void setDescription(String description) {
	this.description = description;
    }

    /**
     * Restituisce il numero di versione.
     */
    public String getVersionLabel() {
	return this.versionLabel;
    }

    /**
     * Imposta il numero di versione.
     */
    public void setVersionLabel(String versionLabel) {
	this.versionLabel = versionLabel;
    }

    /**
     * Restituisce le propriet&agrave; della versione.
     */
    public final void setVersionProperties(Property[] values) {
	this.versionProperties.clear();
	for (int i = 0; i < values.length; i++) {
	    this.versionProperties.add(values[i]);
	}
    }

    /**
     * Imposta le propriet&agrave; della versione.
     */
    public final Property[] getVersionProperties() {
	return ((this.versionProperties != null) && (this.versionProperties.size() > 0))
		? (Property[]) this.versionProperties.toArray(new Property[] {})
		: null;
    }

    @Deprecated
    public final Property getVersionProperty(String prefixedName) {
	for (Property property : versionProperties) {
	    if (property.getPrefixedName().equals(prefixedName)) {
		return property;
	    }
	}
	return null;
    }

    @Deprecated
    public final String getVersionPropertyValue(String prefixedName) {
	Property prop = getVersionProperty(prefixedName);
	return (prop != null) ? prop.getValue() : null;
    }

    /**
     * Restituisce il nodo da cui la versione &egrave; stata creata.
     */
    public void setVersionedNode(Node node) {
	this.versionedNode = node;
    }

    /**
     * Imposta il nodo da cui la versione &egrave; stata creata.
     */
    public Node getVersionedNode() {
	return this.versionedNode;
    }
}
